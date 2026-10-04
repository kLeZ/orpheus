package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.orpheus.domain.identification.DurationsByName;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.FingerprintLookupPort;
import org.orpheus.domain.port.RecordingSearchPort;

/**
 * Second chance for a truncated file (ADR-0023, intervention 4): if the AcoustID lookup ran and found
 * nothing, it searches MusicBrainz for the recordings with the same artist and title and repeats the
 * lookup with their duration ({@link DurationsByName}). Outside the cache decorator, which stores both
 * lookups. The search by name has a cache of its own.
 */
@Decorator
@Priority(5)
public abstract class FullLengthDurationLookup implements FingerprintLookupPort {

	private static final Logger LOG = Logger.getLogger(FullLengthDurationLookup.class);
	private static final String CATEGORY = "recording-search-v2";

	@Inject
	@Delegate
	@Any
	FingerprintLookupPort delegate;

	@Inject
	RecordingSearchPort recordingSearch;

	@Inject
	ExternalResponseCache cache;

	@Override
	public FingerprintLookupResult lookup(Track track) {
		FingerprintLookupResult result = delegate.lookup(track);
		// Without a duration the lookup did not run (missing key, fpcalc failed): nothing to repeat.
		if (!result.candidates().isEmpty() || result.fileDurationSeconds().isEmpty()) {
			return result;
		}
		Optional<DurationsByName.Names> names = DurationsByName.names(track);
		if (names.isEmpty()) {
			return result;
		}
		List<RecordingSearchPort.FoundRecording> found = found(names.get());
		for (int duration : DurationsByName.candidates(names.get(), result.fileDurationSeconds().get(), found)) {
			FingerprintLookupResult withDuration = delegate.lookupWithDuration(track, duration);
			if (!withDuration.candidates().isEmpty()) {
				LOG.info("Recognized with the duration of the whole song (" + duration + " s instead of " + result.fileDurationSeconds()
				                                                                                                  .get() + " s): " + track.physicalPath());
				return withDuration;
			}
		}
		return result;
	}

	private List<RecordingSearchPort.FoundRecording> found(DurationsByName.Names names) {
		String key = names.artist() + "|" + names.title();
		if (cache.enabled()) {
			Optional<List<RecordingSearchPort.FoundRecording>> cached = cache.read(CATEGORY, key, new TypeReference<>() {
			});
			if (cached.isPresent()) {
				return cached.get();
			}
		}
		Optional<List<RecordingSearchPort.FoundRecording>> response = recordingSearch.search(names.artist(), names.title());
		if (cache.enabled()) {
			response.ifPresent(r -> cache.write(CATEGORY, key, r));
		}
		return response.orElse(List.of());
	}
}
