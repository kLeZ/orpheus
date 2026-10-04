package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.orpheus.domain.identification.*;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.musicbrainz.dto.ArtistCreditDto;
import org.orpheus.musicbrainz.dto.MediumDto;
import org.orpheus.musicbrainz.dto.RecordingLookupResponseDto;
import org.orpheus.musicbrainz.dto.ReleaseDto;
import org.orpheus.musicbrainz.dto.ReleaseGroupDto;
import org.orpheus.musicbrainz.dto.TrackDto;

/**
 * MusicBrainz adapter of the domain port {@link ReleaseLookupPort} (SPEC.md section 4.4, ADR-014):
 * lookup of a recording already identified by fingerprint, translated into the candidate
 * {@link Release}s for {@link ReleaseSelector}. It feeds Tier 2, which must never stop because of a
 * network problem: any failure of the lookup (IO, HTTP, timeout, parsing, unknown recording id) is
 * caught, logged and turned into an empty list, never propagated.
 */
@ApplicationScoped
public class MusicBrainzReleaseLookup implements ReleaseLookupPort {

	private static final Logger LOG = Logger.getLogger(MusicBrainzReleaseLookup.class);
	private static final int MAX_ATTEMPTS = 4;
	// Soundtrack excluded on purpose: "Help!" by the Beatles is classified Soundtrack and is the right
	// release for its songs (ADR-0018, decision of 2026-09-26).
	private static final Set<String> PENALIZED_SECONDARY_TYPES = Set.of("compilation", "demo", "live");
	private static final long BACKOFF_BASE_MILLIS = 1000L;

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public List<Release> releasesFor(String recordingId) {
		Exception lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				rateLimiter.awaitTurn();
				RecordingLookupResponseDto response = client.lookup(recordingId, "releases+release-groups+artist-credits+media", "json");
				return validReleases(response, recordingId);
			} catch (Exception e) {
				lastError = e;
				if (!retryable(e) || attempt == MAX_ATTEMPTS) {
					break;
				}
				long wait = BACKOFF_BASE_MILLIS * (1L << (attempt - 1));
				try {
					Thread.sleep(wait);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					LOG.warnf("Interrupted while waiting to retry MusicBrainz for recording %s", recordingId);
					break;
				}
			}
		}
		LOG.warnf(lastError, "MusicBrainz lookup failed after %d attempts for recording %s: no release available for Tier 2", MAX_ATTEMPTS, recordingId);
		return List.of();
	}

	private static boolean retryable(Exception e) {
		if (e instanceof WebApplicationException wae) {
			int status = wae.getResponse().getStatus();
			return status == 429 || status == 502 || status == 503 || status == 504;
		}
		return true;
	}

	private static List<Release> validReleases(RecordingLookupResponseDto response, String recordingId) {
		if (response == null || response.releases() == null) {
			return List.of();
		}
		List<Release> releases = new ArrayList<>();
		for (ReleaseDto release : response.releases()) {
			ReleaseDate releaseDate = parseDate(release.date());
			if (releaseDate == null) {
				// No Release can be built without a date (ReleaseDate always requires one): the release is
				// discarded instead of faking a date the data does not provide.
				continue;
			}
			releases.add(new Release(release.id(), typeOf(release.releaseGroup()), releaseDate, artistsOf(release), isSecondary(release), infoOf(release, recordingId)));
		}
		return releases;
	}

	// The position comes from the first medium that contains the recording.
	private static ReleaseInfo infoOf(ReleaseDto release, String recordingId) {
		Integer disc = null;
		Integer number = null;
		String trackTitle = null;
		List<ArtistCreditDto> trackCredit = List.of();
		if (release.media() != null) {
			for (MediumDto medium : release.media()) {
				if (medium.tracks() != null && !medium.tracks().isEmpty()) {
					TrackDto track = medium.tracks().get(0);
					disc = medium.position();
					number = track.position();
					trackTitle = track.title();
					trackCredit = track.artistCredit() == null ? List.of() : track.artistCredit();
					break;
				}
			}
		}
		List<ArtistCreditDto> releaseCredit = release.artistCredit() == null ? List.of() : release.artistCredit();
		String releaseGroupId = release.releaseGroup() == null ? null : release.releaseGroup().id();
		String trackCreditText = trackCredit.isEmpty() ? null : credit(trackCredit);
		return new ReleaseInfo(release.title() == null ? "" : release.title(), credit(releaseCredit), recordingId, disc, number, trackTitle, releaseGroupId, trackCreditText, ids(trackCredit), ids(releaseCredit));
	}

	// The credit is rebuilt with the MusicBrainz join phrases ("Queen & David Bowie").
	private static String credit(List<ArtistCreditDto> credits) {
		StringBuilder s = new StringBuilder();
		for (ArtistCreditDto c : credits) {
			if (c.name() != null) {
				s.append(c.name()).append(c.joinphrase() == null ? "" : c.joinphrase());
			}
		}
		return s.toString().strip();
	}

	private static List<String> ids(List<ArtistCreditDto> credits) {
		return credits.stream()
		              .filter(c -> c.artist() != null && c.artist().id() != null)
		              .map(c -> c.artist().id())
		              .toList();
	}

	private static List<String> artistsOf(ReleaseDto release) {
		if (release.artistCredit() == null) {
			return List.of();
		}
		return release.artistCredit().stream().map(ArtistCreditDto::name).filter(Objects::nonNull).toList();
	}

	private static boolean isSecondary(ReleaseDto release) {
		if (release.status() != null && "bootleg".equalsIgnoreCase(release.status())) {
			return true;
		}
		ReleaseGroupDto releaseGroup = release.releaseGroup();
		return releaseGroup != null && releaseGroup.secondaryTypes() != null && releaseGroup.secondaryTypes()
		                                                                                    .stream()
		                                                                                    .anyMatch(t -> t != null && PENALIZED_SECONDARY_TYPES.contains(t.toLowerCase(Locale.ROOT)));
	}

	private static ReleaseDate parseDate(String date) {
		if (date == null || date.isBlank()) {
			return null;
		}
		String[] parts = date.split("-");
		try {
			int year = Integer.parseInt(parts[0]);
			if (parts.length >= 3) {
				return ReleaseDate.exact(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
			}
			// 1 part ("YYYY") or 2 parts ("YYYY-MM"): the domain does not model the year and month precision
			// without a day, so year and month are downgraded to year only instead of inventing a fake day
			// (exactly the bug that originated ADR-001).
			return ReleaseDate.yearOnly(year);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static ReleaseType typeOf(ReleaseGroupDto releaseGroup) {
		if (releaseGroup == null || releaseGroup.primaryType() == null) {
			return ReleaseType.OTHER;
		}
		// Case-insensitive for robustness: the real API uses "Album"/"Single"/"EP" (checked with a live
		// call at design time), but there is no reason to be strict on the exact case.
		return switch (releaseGroup.primaryType().toUpperCase(Locale.ROOT)) {
			case "ALBUM" -> ReleaseType.ALBUM;
			case "SINGLE" -> ReleaseType.SINGLE;
			case "EP" -> ReleaseType.EP;
			default -> ReleaseType.OTHER;
		};
	}
}
