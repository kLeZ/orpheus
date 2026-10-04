package org.orpheus.domain.identification;

import org.orpheus.domain.model.Track;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of the fingerprint lookup for a track (ADR-0018): the duration of the audio file as measured
 * by the fingerprint computation ({@link Track} carries no duration) and the candidate recordings in
 * the order given by the adapter.
 */
public record FingerprintLookupResult(Optional<Integer> fileDurationSeconds, List<CandidateRecording> candidates) {

	public FingerprintLookupResult {
		Objects.requireNonNull(fileDurationSeconds, "fileDurationSeconds");
		Objects.requireNonNull(candidates, "candidates");
		candidates = List.copyOf(candidates);
	}

	public static FingerprintLookupResult empty() {
		return new FingerprintLookupResult(Optional.empty(), List.of());
	}

	/** Candidates known only by id, file duration unknown. */
	public static FingerprintLookupResult idsOnly(List<String> recordingIds) {
		return new FingerprintLookupResult(Optional.empty(), recordingIds.stream()
		                                                                 .map(CandidateRecording::idOnly)
		                                                                 .toList());
	}
}
