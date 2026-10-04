package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RawSignalTest {

	@Test
	void matchDistanceCarriesValueAndConfidence() {
		var signal = new RawSignal.MatchDistance(0.12, 0.8);

		assertThat(signal.distance()).isEqualTo(0.12);
		assertThat(signal.confidence()).isEqualTo(0.8);
	}

	@Test
	void durationDeviationRejectsZeroConfidence() {
		assertThatThrownBy(() -> new RawSignal.DurationDeviation(0.3, 0.0))
		                                                                   .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void spectralAnomalyRejectsConfidenceAboveOne() {
		assertThatThrownBy(() -> new RawSignal.SpectralAnomaly(2.5, 1.5))
		                                                                 .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fingerprintScoreRejectsANegativeValue() {
		assertThatThrownBy(() -> new RawSignal.FingerprintScore(-1.0, 0.9))
		                                                                   .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void confidenceOneIsValid() {
		var signal = new RawSignal.FingerprintScore(0.99, 1.0);

		assertThat(signal.confidence()).isEqualTo(1.0);
	}
}
