package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.orpheus.domain.port.TracklistPort;

/** Cache decorator for the tracklist of a release (see {@link ExternalResponseCache}). */
@Decorator
@Priority(10)
public abstract class CachedTracklist implements TracklistPort {

	private static final String CATEGORY = "tracklist-v1";

	@Inject
	@Delegate
	@Any
	TracklistPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public Optional<List<String>> titles(String releaseId) {
		if (!cache.enabled()) {
			return delegate.titles(releaseId);
		}
		Optional<List<String>> cached = cache.read(CATEGORY, releaseId, new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return cached;
		}
		Optional<List<String>> response = delegate.titles(releaseId);
		response.ifPresent(r -> cache.write(CATEGORY, releaseId, r));
		return response;
	}
}
