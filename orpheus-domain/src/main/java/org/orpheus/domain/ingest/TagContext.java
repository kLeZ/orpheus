package org.orpheus.domain.ingest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.model.Track;

/**
 * What the rules of ADR-0021 need to know about a file. {@code folderArtist}, {@code albumTitle} and
 * {@code albumYear} are the same values used for the destination path (principle 1: folder and tags
 * say the same thing); {@code genres} are already chosen according to G01/G02, most voted first, at
 * most five. {@code canonicalTrackArtist} is the canonical form of the tag artist
 * ({@link CanonicalArtistNames}), the same that ends up in the folder: if it differs from the tag, the
 * tag is aligned. {@code composers} are the authors of the recording according to MusicBrainz, empty
 * if unknown. {@code ownedCompilation} is true for every track placed on a Various Artists compilation
 * (user decision of 2026-10-04: compilations always go in "Various Artists").
 */
public record TagContext(Track track,
                         Optional<Release> release,
                         boolean ownedCompilation,
                         boolean multiDisc,
                         @Nullable String folderArtist,
                         @Nullable String albumTitle,
                         @Nullable Integer albumYear,
                         List<String> genres,
                         @Nullable String canonicalTrackArtist,
                         List<String> composers) {

	public TagContext {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(release, "release");
		genres = genres == null ? List.of() : List.copyOf(genres);
		composers = composers == null ? List.of() : List.copyOf(composers);
	}

	public TagContext(Track track, Optional<Release> release, boolean ownedCompilation, boolean multiDisc, @Nullable String folderArtist, @Nullable String albumTitle, @Nullable Integer albumYear, List<String> genres, @Nullable String canonicalTrackArtist) {
		this(track, release, ownedCompilation, multiDisc, folderArtist, albumTitle, albumYear, genres, canonicalTrackArtist, List.of());
	}

	public TagContext(Track track, Optional<Release> release, boolean ownedCompilation, boolean multiDisc, @Nullable String folderArtist, @Nullable String albumTitle, @Nullable Integer albumYear, List<String> genres) {
		this(track, release, ownedCompilation, multiDisc, folderArtist, albumTitle, albumYear, genres, null);
	}
}
