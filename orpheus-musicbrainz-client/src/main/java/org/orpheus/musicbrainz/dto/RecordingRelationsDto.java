package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response of {@code /ws/2/recording/{id}?inc=work-rels+work-level-rels+artist-rels}: the "performance"
 * relations carry the work, with its "composer", "writer", "lyricist" relations. Format checked with a
 * real call on 2026-10-04.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordingRelationsDto(List<Relation> relations) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Relation(String type, Work work, ArtistDto artist) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Work(String title, List<Relation> relations) {
	}
}
