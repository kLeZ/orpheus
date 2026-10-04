package org.orpheus.acoustid;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.acoustid.dto.AcoustIdLookupResponse;
import org.orpheus.acoustid.dto.AcoustIdRecordingDto;
import org.orpheus.acoustid.dto.AcoustIdResultDto;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

/**
 * Pure unit test (no {@code @QuarkusTest}): builds the adapter passing the dependencies explicitly
 * through the same constructor CDI uses, to check in isolation the optimization "do not spawn fpcalc if
 * the API key is missing" (ADR-014, it avoids a useless external process when the HTTP call is known to
 * fail anyway) with a fake {@link FpcalcClient} that counts the invocations. The full path with a real
 * HTTP lookup is covered by {@link AcoustIdFingerprintLookupQuarkusTest}.
 */
class AcoustIdFingerprintLookupTest {

	@Test
	void aMissingApiKeyReturnsAnEmptyResultWithoutRunningFpcalc(@TempDir Path dir) {
		CountingFpcalcClient fpcalc = new CountingFpcalcClient();
		AcoustIdFingerprintLookup port = new AcoustIdFingerprintLookup(
		        fpcalc, null, new AcoustIdRateLimiter(), Optional.empty());

		FingerprintLookupResult result = port.lookup(sampleTrack(dir.resolve("track.mp3")));

		assertThat(result.candidates()).isEmpty();
		assertThat(fpcalc.calls).isZero();
	}

	@Test
	void aBlankApiKeyBehavesAsMissing(@TempDir Path dir) {
		CountingFpcalcClient fpcalc = new CountingFpcalcClient();
		AcoustIdFingerprintLookup port = new AcoustIdFingerprintLookup(
		        fpcalc, null, new AcoustIdRateLimiter(), Optional.of("  "));

		FingerprintLookupResult result = port.lookup(sampleTrack(dir.resolve("track.mp3")));

		assertThat(result.candidates()).isEmpty();
		assertThat(fpcalc.calls).isZero();
	}

	// Sentenced with a wrong Xing header (ADR-0023): no result with the declared duration, the song is
	// found with the decoded one, which reaches the filter of ADR-0018.
	@Test
	void withoutResultsItRetriesWithTheDecodedDuration(@TempDir Path dir) {
		List<Integer> requestedDurations = new java.util.ArrayList<>();
		AcoustIdRestClient rest = (client, fingerprint, duration, meta) -> {
			requestedDurations.add(duration);
			if (duration != 224) {
				return new AcoustIdLookupResponse("ok", List.of());
			}
			return new AcoustIdLookupResponse("ok", List.of(new AcoustIdResultDto("r", 0.98, List.of(new AcoustIdRecordingDto("33333333-3333-3333-3333-333333333333", "The Suicider", List.of(), 224.0)))));
		};
		FpcalcClient fpcalc = new FpcalcClient() {
			@Override
			public Optional<FpcalcResult> compute(Path file) {
				return Optional.of(new FpcalcResult("fp", 347));
			}

			@Override
			public Optional<FpcalcResult> computeFromDecoding(Path file) {
				return Optional.of(new FpcalcResult("fp", 224, true));
			}
		};
		AcoustIdFingerprintLookup port = new AcoustIdFingerprintLookup(fpcalc, rest, new AcoustIdRateLimiter(), Optional.of("key"));

		FingerprintLookupResult result = port.lookup(sampleTrack(dir.resolve("track.mp3")));

		assertThat(requestedDurations).containsExactly(347, 224);
		assertThat(result.fileDurationSeconds()).hasValue(224);
		assertThat(result.candidates()).singleElement().satisfies(c -> assertThat(c.score()).hasValue(0.98));
	}

	private static Track sampleTrack(Path path) {
		TrackMetadata metadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds
		                                                                                                .unknown());
		return new Track(path, metadata, new Provenance(path, ProvenanceState.INTACT));
	}

	private static final class CountingFpcalcClient extends FpcalcClient {

		int calls = 0;

		@Override
		public Optional<FpcalcResult> compute(Path file) {
			calls++;
			return Optional.empty();
		}
	}
}
