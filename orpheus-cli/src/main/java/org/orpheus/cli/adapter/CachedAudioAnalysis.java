package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.port.AudioAnalysisPort;

/**
 * Cache decorator for the integrity analysis (ADR-0023): a full decoding costs about one second of CPU
 * per file, a repeated run reads it from the cache. Same key as the fingerprint lookup (path, size,
 * modification time).
 */
@Decorator
@Priority(10)
public abstract class CachedAudioAnalysis implements AudioAnalysisPort {

	// Version in the name: it must be increased when the shape or the meaning of the analysis changes.
	private static final String CATEGORY = "audio-analysis-v3";

	record CachedAnalysis(@Nullable Double declaredDurationSeconds,
	                      double decodedDurationSeconds,
	                      int decodingErrors,
	                      List<Dropout> dropouts,
	                      double finalLevelDb,
	                      @Nullable List<Double> lineTones) {
	}

	@Inject
	@Delegate
	@Any
	AudioAnalysisPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public Optional<Analysis> analyze(Path file) {
		Optional<String> key = cache.enabled() ? ExternalResponseCache.fileKey(file) : Optional.empty();
		if (key.isEmpty()) {
			return delegate.analyze(file);
		}
		Optional<CachedAnalysis> cached = cache.read(CATEGORY, key.get(), new TypeReference<>() {
		});
		if (cached.isPresent()) {
			CachedAnalysis a = cached.get();
			return Optional.of(new Analysis(Optional.ofNullable(a.declaredDurationSeconds()), a.decodedDurationSeconds(), a.decodingErrors(), a.dropouts(), a.finalLevelDb(), a.lineTones()));
		}
		Optional<Analysis> analysis = delegate.analyze(file);
		analysis.ifPresent(a -> cache.write(CATEGORY, key.get(), new CachedAnalysis(a.declaredDurationSeconds()
		                                                                             .orElse(null), a.decodedDurationSeconds(), a.decodingErrors(), a.dropouts(), a.finalLevelDb(), a.lineTones())));
		return analysis;
	}
}
