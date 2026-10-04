package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response of {@code /ws/2/recording?query=...}; {@code length} in milliseconds. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordingSearchDto(List<Entry> recordings) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Entry(String id,
	                    String title,
	                    Integer length,
	                    @JsonProperty("artist-credit") List<ArtistCreditDto> artistCredit) {
	}
}
