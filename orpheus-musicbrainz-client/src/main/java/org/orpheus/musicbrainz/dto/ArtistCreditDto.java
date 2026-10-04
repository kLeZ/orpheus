package org.orpheus.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An element of the artist credit of a release ({@code artist-credit}), present with {@code inc=artist-credits}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtistCreditDto(String name, String joinphrase, ArtistDto artist) {
}
