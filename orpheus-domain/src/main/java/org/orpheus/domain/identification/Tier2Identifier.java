package org.orpheus.domain.identification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.matching.NormalizedLevenshtein;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Tier 2 identification of a single track (SPEC.md section 4.4): audio fingerprint -&gt; candidate
 * recordings (AcoustID) -&gt; filter of the unrelated recordings ({@link CandidateRecordingFilter},
 * ADR-0018) -&gt; known releases for each survivor (MusicBrainz) -&gt; choice among the aggregated
 * candidates ({@link ReleaseSelector}). A building block reusable both for an isolated track (direct
 * Tier 2) and as the first step of the multi-track consensus (SPEC.md 4.5,
 * {@link MultiTrackConsensus}), which also needs the candidates not selected for the safeguard on the
 * minority tracks.
 *
 * <p>Before the selection, secondary releases (compilations, demos, live recordings, bootlegs) and
 * releases credited to an artist other than the recording's are set aside as long as at least one
 * other release remains (ADR-0018 point B): a Various Artists compilation or a demo older than the
 * original album must not beat it.
 */
public final class Tier2Identifier {

	private static final String VARIOUS_ARTISTS = "various artists";

	private Tier2Identifier() {
	}

	public static Tier2Candidacy identify(Track track, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup) {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(fingerprintLookup, "fingerprintLookup");
		Objects.requireNonNull(releaseLookup, "releaseLookup");

		CandidateRecordingFilter.Result filtered = CandidateRecordingFilter.filter(track, fingerprintLookup.lookup(track));
		Tier2Candidacy candidacy = identify(track, filtered, releaseLookup);
		if (candidacy.candidateReleases().isEmpty() && filtered.fallback().isPresent()) {
			return identify(track, filtered.fallback().get(), releaseLookup);
		}
		return candidacy;
	}

	private static Tier2Candidacy identify(Track track, CandidateRecordingFilter.Result filtered, ReleaseLookupPort releaseLookup) {
		List<Release> candidateReleases = new ArrayList<>();
		List<Release> artistReleases = new ArrayList<>();
		for (CandidateRecording recording : filtered.survivors()) {
			for (Release release : releaseLookup.releasesFor(recording.recordingId())) {
				candidateReleases.add(release);
				if (byTheArtist(release, recording)) {
					artistReleases.add(release);
				}
			}
		}
		List<Release> forSelection = artistReleases.isEmpty() ? candidateReleases : artistReleases;
		// With a known title, the releases where the track carries that title win: the same recording
		// also appears on demo collections with working titles ("In the End" as "The Untitled" on a
		// release of "Hybrid Theory", on the real run).
		List<Release> withTitle = withTrackTitle(forSelection, track.metadata().title());
		if (!withTitle.isEmpty()) {
			forSelection = withTitle;
		}
		return new Tier2Candidacy(track, candidateReleases, ReleaseSelector.choose(forSelection), filtered.artistContrastingTag(), filtered.survivors());
	}

	private static List<Release> withTrackTitle(List<Release> releases, @Nullable String title) {
		if (title == null || key(title).isEmpty()) {
			return List.of();
		}
		String t = key(title);
		List<Release> similar = releases.stream()
		                                .filter(e -> e.info() != null && e.info()
		                                                                  .trackTitle() != null && similar(t, key(e.info()
		                                                                                                           .trackTitle())))
		                                .toList();
		// As for recordings: "Always" is not "Always (live in Montreal)" of the reissue of "These Days"
		// (item in docs/TODO.md, real run of 2026-10-04).
		List<Release> sameVersion = similar.stream()
		                                   .filter(e -> !CandidateRecordingFilter.otherVersion(title, e.info()
		                                                                                               .trackTitle()))
		                                   .toList();
		return sameVersion.isEmpty() ? similar : sameVersion;
	}

	private static boolean similar(String a, String b) {
		return !b.isEmpty() && (a.contains(b) || b.contains(a) || NormalizedLevenshtein.distance(a, b) <= 0.3);
	}

	private static String key(String s) {
		StringBuilder k = new StringBuilder();
		java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFKD)
		                    .codePoints()
		                    .filter(Character::isLetterOrDigit)
		                    .forEach(k::appendCodePoint);
		return k.toString();
	}

	// Release credit or recording artists unknown: not verifiable, so not penalized.
	private static boolean byTheArtist(Release release, CandidateRecording recording) {
		if (release.secondary()) {
			return false;
		}
		if (release.artists()
		           .stream()
		           .anyMatch(a -> a != null && a.strip().toLowerCase(Locale.ROOT).equals(VARIOUS_ARTISTS))) {
			return false;
		}
		if (release.artists().isEmpty() || recording.artists().isEmpty()) {
			return true;
		}
		return recording.artists().stream().anyMatch(a -> ArtistComparison.matches(a, release.artists()));
	}
}
