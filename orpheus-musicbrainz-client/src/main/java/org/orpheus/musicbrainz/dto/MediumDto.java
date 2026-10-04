package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A medium (disc) of a release, present with {@code inc=media}: in the lookup of a recording
 * {@code tracks} contains only the tracks of that recording, with their position.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MediumDto(Integer position, List<TrackDto> tracks) {
}
