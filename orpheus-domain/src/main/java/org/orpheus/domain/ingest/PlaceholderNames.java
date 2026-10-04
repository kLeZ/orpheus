package org.orpheus.domain.ingest;

import java.util.Objects;

/**
 * The names Orpheus uses in paths when a piece of information is missing (user decision of
 * 2026-09-28: in English, and configurable with the {@code orpheus.placeholders.*} properties).
 * {@code disc} and {@code version} are the words of "Disc 2" and "(version 2)".
 */
public record PlaceholderNames(String unknownArtist,
                               String unknownYear,
                               String unknownTrack,
                               String unidentified,
                               String disc,
                               String version) {

	public static final PlaceholderNames DEFAULTS = new PlaceholderNames("Unknown Artist", "Unknown Year", "Unknown Track", "Unidentified", "Disc", "version");

	public PlaceholderNames {
		Objects.requireNonNull(unknownArtist, "unknownArtist");
		Objects.requireNonNull(unknownYear, "unknownYear");
		Objects.requireNonNull(unknownTrack, "unknownTrack");
		Objects.requireNonNull(unidentified, "unidentified");
		Objects.requireNonNull(disc, "disc");
		Objects.requireNonNull(version, "version");
	}
}
