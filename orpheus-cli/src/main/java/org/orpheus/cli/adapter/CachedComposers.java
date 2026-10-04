package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.orpheus.domain.port.ComposersPort;

/** Cache decorator for the authors of a recording (see {@link ExternalResponseCache}). */
@Decorator
@Priority(10)
public abstract class CachedComposers implements ComposersPort {

	private static final String CATEGORY = "composers-v1";

	@Inject
	@Delegate
	@Any
	ComposersPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public Optional<List<String>> composers(String recordingId) {
		if (!cache.enabled()) {
			return delegate.composers(recordingId);
		}
		Optional<List<String>> cached = cache.read(CATEGORY, recordingId, new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return cached;
		}
		Optional<List<String>> response = delegate.composers(recordingId);
		response.ifPresent(r -> cache.write(CATEGORY, recordingId, r));
		return response;
	}
}
