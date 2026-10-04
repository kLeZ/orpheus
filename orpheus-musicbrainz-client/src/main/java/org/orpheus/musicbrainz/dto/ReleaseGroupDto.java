package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.musicbrainz.MusicBrainzReleaseLookup;

import java.util.List;

/**
 * The release group of a release (ADR-014): {@code primaryType} is, among others, {@code "Album"},
 * {@code "Single"} or {@code "EP"} (checked with a real call to the API at design time), or absent when
 * MusicBrainz does not classify it. The mapping to {@link ReleaseType} lives in
 * {@link org.orpheus.musicbrainz.MusicBrainzReleaseLookup}, not here. {@code secondaryTypes} (e.g.
 * {@code "Compilation"}, {@code "Live"}) is used to recognize compilations (ADR-0018).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseGroupDto(String id,
                              @JsonProperty("primary-type") String primaryType,
                              @JsonProperty("secondary-types") List<String> secondaryTypes) {
}
