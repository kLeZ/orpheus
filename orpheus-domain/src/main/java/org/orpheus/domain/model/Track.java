package org.orpheus.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.LocalEvidence;

/**
 * An audio file with its current metadata, its physical path and its provenance state (SPEC.md
 * section 3). {@code artistFromPath} is the name of the top-level folder under the source root, if
 * the file is inside one: weak evidence, used only as a fallback for the destination folder when no
 * tag names the artist, never as a known artist for identification (folders like "Punk Cover" are
 * not artists). {@code tagMetadata} is the metadata exactly as read from the tags, before cleanup and
 * completion ({@link LocalEvidence}): it tells where each value comes from (ADR-0021, {@code FILL}
 * rules); null until someone has told it apart from {@code metadata}. {@code tagGenres} are the
 * genres as read from the file (ADR-0021, Genre section).
 */
public record Track(Path physicalPath,
                    TrackMetadata metadata,
                    Provenance provenance,
                    @Nullable String artistFromPath,
                    @Nullable TrackMetadata tagMetadata,
                    List<String> tagGenres) {

	public Track {
		Objects.requireNonNull(physicalPath, "physicalPath");
		Objects.requireNonNull(metadata, "metadata");
		Objects.requireNonNull(provenance, "provenance");
		tagGenres = tagGenres == null ? List.of() : List.copyOf(tagGenres);
	}

	public Track(Path physicalPath, TrackMetadata metadata, Provenance provenance) {
		this(physicalPath, metadata, provenance, null, null, List.of());
	}

	public Track(Path physicalPath, TrackMetadata metadata, Provenance provenance, @Nullable String artistFromPath) {
		this(physicalPath, metadata, provenance, artistFromPath, null, List.of());
	}

	public Track(Path physicalPath, TrackMetadata metadata, Provenance provenance, @Nullable String artistFromPath, @Nullable TrackMetadata tagMetadata) {
		this(physicalPath, metadata, provenance, artistFromPath, tagMetadata, List.of());
	}

	public Track withMetadata(TrackMetadata newMetadata) {
		return new Track(physicalPath, newMetadata, provenance, artistFromPath, tagMetadata, tagGenres);
	}

	public Track withTagGenres(List<String> genres) {
		return new Track(physicalPath, metadata, provenance, artistFromPath, tagMetadata, genres);
	}

	/** The metadata as read from the tags: the preserved one, or the current one if never told apart. */
	public TrackMetadata originalMetadata() {
		return tagMetadata != null ? tagMetadata : metadata;
	}
}
