package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A MusicBrainz release known for a recording identified by fingerprint, a candidate for
 * {@link ReleaseSelector} (SPEC.md section 4.4, ADR-001). {@code artists} is the credit of the release
 * (empty if unknown); {@code secondary} is true for compilations, demos, live recordings or bootlegs:
 * they make these releases lose against the ordinary releases of the artist (ADR-0018 point B and the
 * following decision of 2026-09-26). {@code info} places the track in the release (title, position),
 * if known.
 */
public record Release(String releaseId,
                      ReleaseType type,
                      ReleaseDate releaseDate,
                      List<String> artists,
                      boolean secondary,
                      @Nullable ReleaseInfo info) {

	public Release {
		Objects.requireNonNull(releaseId, "releaseId");
		if (releaseId.isBlank()) {
			throw new IllegalArgumentException("releaseId cannot be blank");
		}
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(releaseDate, "releaseDate");
		Objects.requireNonNull(artists, "artists");
		artists = List.copyOf(artists);
	}

	/** A release whose artist and secondary type are unknown. */
	public Release(String releaseId, ReleaseType type, ReleaseDate releaseDate) {
		this(releaseId, type, releaseDate, List.of(), false, null);
	}

	public Release(String releaseId, ReleaseType type, ReleaseDate releaseDate, List<String> artists, boolean secondary) {
		this(releaseId, type, releaseDate, artists, secondary, null);
	}
}
