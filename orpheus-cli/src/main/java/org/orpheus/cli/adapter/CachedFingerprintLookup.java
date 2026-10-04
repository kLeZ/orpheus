package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Cache decorator for the fingerprint lookup (see {@link ExternalResponseCache}). The key is absolute
 * path, size and modification time of the file, so a repeated run does not even recompute the
 * fingerprint with fpcalc; a modified file changes key.
 */
@Decorator
@Priority(10)
public abstract class CachedFingerprintLookup implements FingerprintLookupPort {

	// Version in the name: it must be increased when the shape or the meaning of what the adapter returns
	// changes, otherwise a check run would read old results without any error.
	private static final String CATEGORY = "acoustid-v4";

	record CachedCandidate(String recordingId,
	                       @Nullable String title,
	                       List<String> artists,
	                       @Nullable Integer durationSeconds,
	                       @Nullable Double score,
	                       @Nullable Integer associations) {
	}

	record CachedResult(@Nullable Integer fileDurationSeconds, List<CachedCandidate> candidates) {
	}

	@Inject
	@Delegate
	@Any
	FingerprintLookupPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public FingerprintLookupResult lookup(Track track) {
		if (!cache.enabled()) {
			return delegate.lookup(track);
		}
		Optional<String> key = ExternalResponseCache.fileKey(track.provenance().source());
		if (key.isEmpty()) {
			return delegate.lookup(track);
		}
		Optional<CachedResult> cached = cache.read(CATEGORY, key.get(), new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return fromCache(cached.get());
		}
		FingerprintLookupResult result = delegate.lookup(track);
		if (!result.candidates().isEmpty()) {
			cache.write(CATEGORY, key.get(), toCache(result));
		}
		return result;
	}

	@Override
	public FingerprintLookupResult lookupWithDuration(Track track, int durationSeconds) {
		Optional<String> key = cache.enabled() ? ExternalResponseCache.fileKey(track.provenance().source())
		                                                              .map(k -> k + "|duration=" + durationSeconds) : Optional.empty();
		if (key.isEmpty()) {
			return delegate.lookupWithDuration(track, durationSeconds);
		}
		Optional<CachedResult> cached = cache.read(CATEGORY, key.get(), new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return fromCache(cached.get());
		}
		FingerprintLookupResult result = delegate.lookupWithDuration(track, durationSeconds);
		if (!result.candidates().isEmpty()) {
			cache.write(CATEGORY, key.get(), toCache(result));
		}
		return result;
	}

	private static CachedResult toCache(FingerprintLookupResult r) {
		return new CachedResult(r.fileDurationSeconds().orElse(null), r.candidates()
		                                                               .stream()
		                                                               .map(c -> new CachedCandidate(c.recordingId(), c.title(), c.artists(), c.durationSeconds()
		                                                                                                                                       .orElse(null), c.score()
		                                                                                                                                                       .orElse(null), c.associations()
		                                                                                                                                                                       .orElse(null)))
		                                                               .toList());
	}

	private static FingerprintLookupResult fromCache(CachedResult r) {
		return new FingerprintLookupResult(Optional.ofNullable(r.fileDurationSeconds()), r.candidates()
		                                                                                  .stream()
		                                                                                  .map(c -> new CandidateRecording(c.recordingId(), c.title(), c.artists(), Optional.ofNullable(c.durationSeconds()), Optional.ofNullable(c.score()), Optional.ofNullable(c.associations())))
		                                                                                  .toList());
	}
}
