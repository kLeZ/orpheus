package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Lookup response of a release group or an artist with {@code inc=genres} (ADR-0021). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenresDto(List<GenreDto> genres) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GenreDto(String name, Integer count) {
	}
}
