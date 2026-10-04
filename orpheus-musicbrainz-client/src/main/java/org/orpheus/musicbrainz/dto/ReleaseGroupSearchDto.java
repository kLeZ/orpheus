package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response of {@code /ws/2/release-group?query=...}: format checked with a real call on 2026-09-27. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseGroupSearchDto(@JsonProperty("release-groups") List<Entry> releaseGroups) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Entry(String id,
	                    String title,
	                    @JsonProperty("primary-type") String primaryType,
	                    @JsonProperty("secondary-types") List<String> secondaryTypes,
	                    @JsonProperty("artist-credit") List<ArtistCreditDto> artistCredit,
	                    @JsonProperty("first-release-date") String firstReleaseDate) {
	}
}
