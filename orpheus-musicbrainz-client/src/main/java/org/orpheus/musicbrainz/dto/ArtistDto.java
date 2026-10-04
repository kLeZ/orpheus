package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The artist of a credit element: the MusicBrainz id is needed (ADR-0021). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtistDto(String id, String name) {

	public ArtistDto(String id) {
		this(id, null);
	}
}
