package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.orpheus.domain.port.ReleaseGroupSearchPort;

/** Deterministic fake of {@link ReleaseGroupSearchPort}, fixed candidates in memory by artist and title. */
@Alternative
@Priority(1)
@ApplicationScoped
public class ReleaseGroupSearchFake implements ReleaseGroupSearchPort {

	private final Map<String, List<FoundReleaseGroup>> candidates = new ConcurrentHashMap<>();

	public void set(String artist, String title, List<FoundReleaseGroup> found) {
		candidates.put(artist + "|" + title, found);
	}

	public void clear() {
		candidates.clear();
	}

	@Override
	public Optional<List<FoundReleaseGroup>> search(String artist, String albumTitle) {
		return Optional.of(candidates.getOrDefault(artist + "|" + albumTitle, List.of()));
	}
}
