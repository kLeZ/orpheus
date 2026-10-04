package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.orpheus.domain.model.Track;

/**
 * Result of the Tier 2 identification of a single track (SPEC.md section 4.4):
 * {@code candidateReleases} is the union of the releases of <b>all</b> the recordings that survived
 * the filter of ADR-0018, not only of the one that produced {@code selectedRelease}: the multi-track
 * consensus (SPEC.md 4.5) needs the full list for the safeguard on the minority tracks ("they too have
 * a match, not necessarily the best one, on the winning release"). {@code artistContrastingTag} is
 * present when the identification comes from the fingerprint against the artist written in the tag
 * (ADR-0018 point C). {@code recordings} are the recordings that survived the filter, from which score
 * and duration of the recording of a release can be traced back (ADR-0023).
 */
public record Tier2Candidacy(Track track,
                             List<Release> candidateReleases,
                             Optional<Release> selectedRelease,
                             Optional<String> artistContrastingTag,
                             List<CandidateRecording> recordings) {

	public Tier2Candidacy {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(candidateReleases, "candidateReleases");
		Objects.requireNonNull(selectedRelease, "selectedRelease");
		Objects.requireNonNull(artistContrastingTag, "artistContrastingTag");
		Objects.requireNonNull(recordings, "recordings");
		candidateReleases = List.copyOf(candidateReleases);
		recordings = List.copyOf(recordings);
	}

	public Tier2Candidacy(Track track, List<Release> candidateReleases, Optional<Release> selectedRelease, Optional<String> artistContrastingTag) {
		this(track, candidateReleases, selectedRelease, artistContrastingTag, List.of());
	}

	public Tier2Candidacy(Track track, List<Release> candidateReleases, Optional<Release> selectedRelease) {
		this(track, candidateReleases, selectedRelease, Optional.empty());
	}

	/**
	 * The recording a release comes from, recognized by the recording id in the placement of the
	 * track; without that id, the only survivor if there is just one.
	 */
	public Optional<CandidateRecording> recordingOf(Release release) {
		String id = release.info() != null ? release.info().recordingId() : null;
		if (id != null) {
			return recordings.stream().filter(r -> r.recordingId().equals(id)).findFirst();
		}
		return recordings.size() == 1 ? Optional.of(recordings.get(0)) : Optional.empty();
	}
}
