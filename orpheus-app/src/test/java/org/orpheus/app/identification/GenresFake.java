package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.orpheus.domain.port.GenresPort;

/** Deterministic fake of {@link GenresPort}, fixed genres in memory by id. */
@Alternative
@Priority(1)
@ApplicationScoped
public class GenresFake implements GenresPort {

	private final Map<String, List<String>> genresById = new ConcurrentHashMap<>();

	public void set(String id, List<String> genres) {
		genresById.put(id, genres);
	}

	public void clear() {
		genresById.clear();
	}

	@Override
	public Optional<List<String>> releaseGroupGenres(String releaseGroupId) {
		return Optional.of(genresById.getOrDefault(releaseGroupId, List.of()));
	}

	@Override
	public Optional<List<String>> artistGenres(String artistId) {
		return Optional.of(genresById.getOrDefault(artistId, List.of()));
	}
}
