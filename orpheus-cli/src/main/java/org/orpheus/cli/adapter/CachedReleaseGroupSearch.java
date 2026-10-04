package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.orpheus.domain.port.ReleaseGroupSearchPort;

/**
 * Cache decorator for the search of release groups by name (see {@link ExternalResponseCache}). As for
 * the genres, an empty list is cached too; an error is not.
 */
@Decorator
@Priority(10)
public abstract class CachedReleaseGroupSearch implements ReleaseGroupSearchPort {

	private static final String CATEGORY = "release-group-search-v4";

	@Inject
	@Delegate
	@Any
	ReleaseGroupSearchPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public Optional<List<FoundReleaseGroup>> search(String artist, String albumTitle) {
		if (!cache.enabled()) {
			return delegate.search(artist, albumTitle);
		}
		String key = artist + "\u0000" + albumTitle;
		Optional<List<FoundReleaseGroup>> cached = cache.read(CATEGORY, key, new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return cached;
		}
		Optional<List<FoundReleaseGroup>> response = delegate.search(artist, albumTitle);
		response.ifPresent(r -> cache.write(CATEGORY, key, r));
		return response;
	}
}
