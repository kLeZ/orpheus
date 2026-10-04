package org.orpheus.domain.model;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A group of tracks with shared metadata, complete according to an external source or partial
 * (SPEC.md section 3). An isolated track, without any album, has no associated {@link Album}: it is
 * not a value of this type, it is the absence of the reference.
 */
public record Album(
                    String title,
                    @Nullable String albumArtist,
                    @Nullable Integer year,
                    AlbumCompleteness completeness,
                    List<Track> tracks) {

	public Album {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("title cannot be blank");
		}
		Objects.requireNonNull(completeness, "completeness");
		Objects.requireNonNull(tracks, "tracks");
		tracks = List.copyOf(tracks);
		if (tracks.isEmpty()) {
			throw new IllegalArgumentException("an album must group at least one track");
		}
	}
}
