package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Deterministic fake of {@link FingerprintLookupPort} for the integration tests of the multi-track
 * consensus and Tier 2 on {@code ImportCommand}: no call to fpcalc or AcoustID, fixed data in memory per
 * source file path (consistent with the style of the project: real objects and fixed data instead of
 * Mockito).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FingerprintLookupFake implements FingerprintLookupPort {

	private final Map<Path, FingerprintLookupResult> resultByFile = new ConcurrentHashMap<>();

	/** Candidates known only by id: they survive the filter of ADR-0018 only if the track has no artist. */
	public void set(Path file, List<String> recordingIds) {
		resultByFile.put(file, FingerprintLookupResult.idsOnly(recordingIds));
	}

	/** Candidates credited to {@code artist}, unknown duration. */
	public void set(Path file, List<String> recordingIds, String artist) {
		resultByFile.put(
		        file, new FingerprintLookupResult(
		                Optional.empty(), recordingIds.stream()
		                                              .map(id -> new CandidateRecording(id, null, List.of(artist), Optional.empty()))
		                                              .toList()));
	}

	/** Candidates credited to {@code artist} with title {@code title}, unknown duration. */
	public void set(Path file, List<String> recordingIds, String artist, String title) {
		resultByFile.put(
		        file, new FingerprintLookupResult(
		                Optional.empty(), recordingIds.stream()
		                                              .map(id -> new CandidateRecording(id, title, List.of(artist), Optional.empty()))
		                                              .toList()));
	}

	public void clear() {
		resultByFile.clear();
	}

	@Override
	public FingerprintLookupResult lookup(Track track) {
		return resultByFile.getOrDefault(track.physicalPath(), FingerprintLookupResult.empty());
	}
}
