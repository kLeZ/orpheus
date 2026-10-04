package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.orpheus.domain.identification.Release;
import org.orpheus.musicbrainz.MusicBrainzReleaseLookup;

import java.util.List;

/**
 * Body of the response of {@code GET /ws/2/recording/{id}?inc=releases+release-groups} (ADR-014). Never
 * exposed outside this module: {@link org.orpheus.musicbrainz.MusicBrainzReleaseLookup} translates it
 * right away into domain {@link Release}s.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordingLookupResponseDto(String id, List<ReleaseDto> releases) {
}
