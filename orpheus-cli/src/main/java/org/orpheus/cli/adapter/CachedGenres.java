package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.orpheus.domain.port.GenresPort;

/**
 * Cache decorator for the MusicBrainz genres (see {@link ExternalResponseCache}). Unlike the other
 * ports, an empty list is cached too: it is a valid answer (no known genre), and without a cache every
 * run would repeat hundreds of calls. Only an error ({@code Optional.empty()}) is not stored.
 */
@Decorator
@Priority(10)
public abstract class CachedGenres implements GenresPort {

	private static final String CATEGORY = "genres-v1";

	@Inject
	@Delegate
	@Any
	GenresPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public Optional<List<String>> releaseGroupGenres(String releaseGroupId) {
		return withCache("release-group:" + releaseGroupId, () -> delegate.releaseGroupGenres(releaseGroupId));
	}

	@Override
	public Optional<List<String>> artistGenres(String artistId) {
		return withCache("artist:" + artistId, () -> delegate.artistGenres(artistId));
	}

	private Optional<List<String>> withCache(String key, Supplier<Optional<List<String>>> call) {
		if (!cache.enabled()) {
			return call.get();
		}
		Optional<List<String>> cached = cache.read(CATEGORY, key, new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return cached;
		}
		Optional<List<String>> response = call.get();
		response.ifPresent(genres -> cache.write(CATEGORY, key, genres));
		return response;
	}
}
