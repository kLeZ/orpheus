package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.port.ReleaseGroupSearchPort;
import org.orpheus.musicbrainz.dto.ArtistCreditDto;
import org.orpheus.musicbrainz.dto.ReleaseGroupSearchDto;

/**
 * Search of release groups by artist and title (ADR-0021, revision of 2026-09-27). The choice among the
 * candidates (exact match) is in the domain, in {@code GenresByName}: here only the translation of the
 * response. Same rate limiter and same retry policy as the other MusicBrainz adapters.
 */
@ApplicationScoped
public class MusicBrainzReleaseGroupSearch implements ReleaseGroupSearchPort {

	private static final Logger LOG = Logger.getLogger(MusicBrainzReleaseGroupSearch.class);
	private static final int MAX_ATTEMPTS = 4;
	private static final long BACKOFF_BASE_MILLIS = 1000L;
	private static final int LIMIT = 25;

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public Optional<List<FoundReleaseGroup>> search(String artist, String albumTitle) {
		String query = "releasegroup:\"" + phrase(albumTitle) + "\" AND artist:\"" + phrase(artist) + "\"";
		return withRetries(rateLimiter, () -> translate(client.searchReleaseGroups(query, LIMIT, "json")), albumTitle, artist);
	}

	// Attempts with backoff on transient errors only, shared by the searches by name.
	static <T> Optional<T> withRetries(MusicBrainzRateLimiter rateLimiter, java.util.function.Supplier<T> call, String title, String artist) {
		Exception lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				rateLimiter.awaitTurn();
				return Optional.of(call.get());
			} catch (WebApplicationException e) {
				lastError = e;
				int status = e.getResponse().getStatus();
				if (!(status == 429 || status == 502 || status == 503 || status == 504) || attempt == MAX_ATTEMPTS) {
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
		LOG.warnf(lastError, "MusicBrainz search failed for \"%s\" by \"%s\"", title, artist);
		return Optional.empty();
	}

	// Inside a quoted Lucene phrase only the quote and the backslash need escaping. "&" between
	// spaces: attached to the words ("Darkness&Hope") it would form a single word in the index.
	static String phrase(String text) {
		return text.replace("&", " & ").replace("\\", "\\\\").replace("\"", "\\\"");
	}

	static List<FoundReleaseGroup> translate(ReleaseGroupSearchDto dto) {
		if (dto == null || dto.releaseGroups() == null) {
			return List.of();
		}
		return dto.releaseGroups().stream().filter(v -> v.id() != null && v.title() != null).map(v -> {
			List<ArtistCreditDto> credit = v.artistCredit() == null ? List.of() : v.artistCredit();
			StringBuilder name = new StringBuilder();
			for (ArtistCreditDto c : credit) {
				name.append(Objects.requireNonNullElse(c.name(), ""))
				    .append(Objects.requireNonNullElse(c.joinphrase(), ""));
			}
			List<String> ids = credit.stream()
			                         .filter(c -> c.artist() != null && c.artist().id() != null)
			                         .map(c -> c.artist().id())
			                         .toList();
			return new FoundReleaseGroup(v.id(), v.title(), name.toString(), ids, v.primaryType(), v.secondaryTypes(), year(v.firstReleaseDate()));
		}).toList();
	}

	// "1996", "1996-05" or "1996-05-27": only the year is needed.
	private static @Nullable Integer year(@Nullable String date) {
		if (date == null || date.length() < 4) {
			return null;
		}
		try {
			return Integer.parseInt(date.substring(0, 4));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
