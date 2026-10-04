package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.port.ReleaseLookupPort;

/** Deterministic fake of {@link ReleaseLookupPort}, fixed data in memory by recordingId. */
@Alternative
@Priority(1)
@ApplicationScoped
public class ReleaseLookupFake implements ReleaseLookupPort {

	private final Map<String, List<Release>> releasesByRecordingId = new ConcurrentHashMap<>();

	public void set(String recordingId, List<Release> releases) {
		releasesByRecordingId.put(recordingId, releases);
	}

	public void clear() {
		releasesByRecordingId.clear();
	}

	@Override
	public List<Release> releasesFor(String recordingId) {
		return releasesByRecordingId.getOrDefault(recordingId, List.of());
	}
}
