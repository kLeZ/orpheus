package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A track on a medium: {@code position} is numeric, {@code number} is the printed label (on vinyl "A1",
 * "B2").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackDto(Integer position,
                       String number,
                       String title,
                       @JsonProperty("artist-credit") List<ArtistCreditDto> artistCredit) {
}
