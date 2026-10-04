package org.orpheus.domain.model;

/**
 * The raw signal of a review item, typed by category and always queryable on its own (SPEC.md
 * section 5): the distance of an applied match, the deviation ratio of a duration, a spectral anomaly
 * score, the match score of an audio fingerprint. The associated confidence follows the convention of
 * ADR-004 (0 excluded, 1 included). It does not implement the priority formula that uses it
 * (ADR-004): that one sums weights chosen elsewhere, the model only carries the raw data.
 */
public sealed interface RawSignal {

	double confidence();

	private static double validConfidence(double confidence) {
		if (!(confidence > 0.0 && confidence <= 1.0)) {
			throw new IllegalArgumentException("confidence must be in (0, 1], was " + confidence);
		}
		return confidence;
	}

	private static double nonNegative(double value, String name) {
		if (value < 0.0) {
			throw new IllegalArgumentException(name + " cannot be negative, was " + value);
		}
		return value;
	}

	record MatchDistance(double distance, double confidence) implements RawSignal {
		public MatchDistance {
			distance = nonNegative(distance, "distance");
			confidence = validConfidence(confidence);
		}
	}

	record DurationDeviation(double ratio, double confidence) implements RawSignal {
		public DurationDeviation {
			ratio = nonNegative(ratio, "ratio");
			confidence = validConfidence(confidence);
		}
	}

	record SpectralAnomaly(double score, double confidence) implements RawSignal {
		public SpectralAnomaly {
			score = nonNegative(score, "score");
			confidence = validConfidence(confidence);
		}
	}

	record FingerprintScore(double score, double confidence) implements RawSignal {
		public FingerprintScore {
			score = nonNegative(score, "score");
			confidence = validConfidence(confidence);
		}
	}

	/**
	 * Seconds of audio lost or suspect in a damaged file (ADR-0023): unreadable frames, missing final
	 * part, drops to digital silence.
	 */
	record MissingAudio(double seconds, double confidence) implements RawSignal {
		public MissingAudio {
			seconds = nonNegative(seconds, "seconds");
			confidence = validConfidence(confidence);
		}
	}

	record TextClassificationConfidence(double confidence) implements RawSignal {
		public TextClassificationConfidence {
			confidence = validConfidence(confidence);
		}
	}
}
