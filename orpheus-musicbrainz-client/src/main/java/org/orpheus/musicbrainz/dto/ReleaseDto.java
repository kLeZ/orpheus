package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.musicbrainz.MusicBrainzReleaseLookup;

import java.util.List;

/**
 * A MusicBrainz release in the {@code releases} list of the lookup response (ADR-014). {@code date} is
 * a string with variable precision ({@code "YYYY"}, {@code "YYYY-MM"} or {@code "YYYY-MM-DD"}) or
 * absent/empty when the date is unknown: the parsing into {@link ReleaseDate} lives in
 * {@link org.orpheus.musicbrainz.MusicBrainzReleaseLookup}, not here. {@code artistCredit} is the credit
 * of the release (ADR-0018), absent if the response does not include it; {@code status} is for example
 * {@code "Official"} or {@code "Bootleg"}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseDto(String id,
                         String title,
                         String date,
                         @JsonProperty("release-group") ReleaseGroupDto releaseGroup,
                         @JsonProperty("artist-credit") List<ArtistCreditDto> artistCredit,
                         String status,
                         List<MediumDto> media) {
}
