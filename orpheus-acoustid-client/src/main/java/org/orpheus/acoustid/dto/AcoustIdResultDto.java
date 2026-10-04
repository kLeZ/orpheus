package org.orpheus.acoustid.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * An element of {@code results[]} in the AcoustID lookup response: a fingerprint match (identified by
 * {@code id}, the AcoustID match id, not a MusicBrainz recording id) with the associated MusicBrainz
 * recordings. The {@code results} array is already sorted by descending AcoustID score on the server,
 * an order this adapter keeps by mapping the recordings in the same order (SPEC.md section 4.4).
 * {@code score} holds for all the recordings of the result (ADR-0023).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcoustIdResultDto(String id, Double score, List<AcoustIdRecordingDto> recordings) {
}
