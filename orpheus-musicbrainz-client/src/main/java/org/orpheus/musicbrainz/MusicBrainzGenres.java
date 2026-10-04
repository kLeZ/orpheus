package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.orpheus.domain.port.GenresPort;
import org.orpheus.musicbrainz.dto.GenresDto;

/**
 * MusicBrainz genres of release groups and artists (ADR-0021, Genre section), by descending votes and
 * alphabetically on a tie. Same rate limiter and same retry policy as the release lookup; a 404
 * (unknown id) is a valid answer without genres.
 */
@ApplicationScoped
public class MusicBrainzGenres implements GenresPort {

	private static final Logger LOG = Logger.getLogger(MusicBrainzGenres.class);
	private static final int MAX_ATTEMPTS = 4;
	private static final long BACKOFF_BASE_MILLIS = 1000L;

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public Optional<List<String>> releaseGroupGenres(String releaseGroupId) {
		return withRetry(() -> client.releaseGroup(releaseGroupId, "genres", "json"), "release group " + releaseGroupId);
	}

	@Override
	public Optional<List<String>> artistGenres(String artistId) {
		return withRetry(() -> client.artist(artistId, "genres", "json"), "artist " + artistId);
	}

	private Optional<List<String>> withRetry(Supplier<GenresDto> call, String description) {
		Exception lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				rateLimiter.awaitTurn();
				return Optional.of(sorted(call.get()));
			} catch (WebApplicationException e) {
				if (e.getResponse().getStatus() == 404) {
					return Optional.of(List.of());
				}
				lastError = e;
				if (!retryable(e.getResponse().getStatus()) || attempt == MAX_ATTEMPTS) {
					break;
				}
			} catch (Exception e) {
				lastError = e;
				if (attempt == MAX_ATTEMPTS) {
					break;
				}
			}
			try {
				Thread.sleep(BACKOFF_BASE_MILLIS * (1L << (attempt - 1)));
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		LOG.warnf(lastError, "MusicBrainz genres not available for %s", description);
		return Optional.empty();
	}

	private static boolean retryable(int status) {
		return status == 429 || status == 502 || status == 503 || status == 504;
	}

	static List<String> sorted(GenresDto dto) {
		if (dto == null || dto.genres() == null) {
			return List.of();
		}
		return dto.genres()
		          .stream()
		          .filter(g -> g.name() != null && !g.name().isBlank())
		          .sorted(Comparator.comparing((GenresDto.GenreDto g) -> g.count() == null ? 0 : g.count())
		                            .reversed()
		                            .thenComparing(GenresDto.GenreDto::name))
		          .map(GenresDto.GenreDto::name)
		          .toList();
	}
}
