package org.orpheus.acoustid.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.orpheus.domain.port.FingerprintLookupPort;

import java.util.List;

/**
 * A MusicBrainz recording associated with a fingerprint match (SPEC.md section 4.4). The {@code id} field
 * is the MusicBrainz recording id this module returns as a candidate through
 * {@link FingerprintLookupPort}, not the internal id of the AcoustID match. {@code title},
 * {@code artists} and {@code duration} (seconds, decimal) may be missing when MusicBrainz does not
 * provide them: they feed the filter of the unrelated recordings (ADR-0018).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcoustIdRecordingDto(String id,
                                   String title,
                                   List<AcoustIdArtistDto> artists,
                                   Double duration,
                                   Integer sources) {

	public AcoustIdRecordingDto(String id, String title, List<AcoustIdArtistDto> artists, Double duration) {
		this(id, title, artists, duration, null);
	}
}
