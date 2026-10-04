package org.orpheus.acoustid.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An artist credited on a recording in the AcoustID lookup response ({@code meta=recordings}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcoustIdArtistDto(String name) {
}
