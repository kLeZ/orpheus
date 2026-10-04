package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.Track;

/**
 * Result of the Tier 0/1 local identification engine (SPEC.md section 4.4): the tracks grouped in an
 * {@link Album} stay separate from those left isolated. An isolated track is neither an error nor a
 * residue to report here: SPEC.md treats it as a normal result, like an album found (DESIGN.md,
 * failure criteria).
 */
public record LocalIdentificationResult(List<Album> albums, List<Track> isolatedTracks) {

	public LocalIdentificationResult {
		Objects.requireNonNull(albums, "albums");
		Objects.requireNonNull(isolatedTracks, "isolatedTracks");
		albums = List.copyOf(albums);
		isolatedTracks = List.copyOf(isolatedTracks);
	}
}
