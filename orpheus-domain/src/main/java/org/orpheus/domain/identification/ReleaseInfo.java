package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What is needed to place a track in the release that contains it and to write its tags (corrective
 * plan, phase 2; ADR-0021): title and artist credit of the release, the recording that produced this
 * release, disc, number and title of the track in the tracklist, the artist credit of the track (in a
 * compilation different from the release's), the MusicBrainz ids of the release group, of the track
 * artists and of the release artists. Disc, number, title and credit of the track are specific to the
 * recording-release pair, not to the release alone.
 */
public record ReleaseInfo(String title,
                          String artistCredit,
                          @Nullable String recordingId,
                          @Nullable Integer disc,
                          @Nullable Integer number,
                          @Nullable String trackTitle,
                          @Nullable String releaseGroupId,
                          @Nullable String trackArtistCredit,
                          List<String> trackArtistIds,
                          List<String> releaseArtistIds) {

	public ReleaseInfo {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(artistCredit, "artistCredit");
		trackArtistIds = trackArtistIds == null ? List.of() : List.copyOf(trackArtistIds);
		releaseArtistIds = releaseArtistIds == null ? List.of() : List.copyOf(releaseArtistIds);
	}

	public ReleaseInfo(String title, String artistCredit, @Nullable String recordingId, @Nullable Integer disc, @Nullable Integer number, @Nullable String trackTitle) {
		this(title, artistCredit, recordingId, disc, number, trackTitle, null, null, List.of(), List.of());
	}

	/** The same release with another title and no disc: for release unification (ADR-0020). */
	public ReleaseInfo asEdition(String releaseTitle) {
		return new ReleaseInfo(releaseTitle, artistCredit, recordingId, null, number, trackTitle, releaseGroupId, trackArtistCredit, trackArtistIds, releaseArtistIds);
	}
}
