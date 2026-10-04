package org.orpheus.domain.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Current metadata of a track: what the tags say now, not necessarily correct or complete (SPEC.md
 * section 2). No range check on year, track number or disc number: a corrupted tag (e.g. year 0 or
 * negative) is the base case to represent, not one to reject here; sanitizing, when it makes sense,
 * belongs to the tag reading adapter.
 */
public record TrackMetadata(
                            @Nullable String artist,
                            @Nullable String title,
                            @Nullable String album,
                            @Nullable String albumArtist,
                            @Nullable Integer year,
                            @Nullable Integer trackNumber,
                            @Nullable Integer discNumber,
                            ExternalIds externalIds) {

	public TrackMetadata {
		Objects.requireNonNull(externalIds, "externalIds");
	}
}
