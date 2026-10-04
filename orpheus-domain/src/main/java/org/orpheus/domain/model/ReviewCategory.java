package org.orpheus.domain.model;

/** The problem category of a review queue item (SPEC.md sections 3 and 5). */
public enum ReviewCategory {
	AUDIO_QUALITY,
	SUSPECTED_EDIT,
	ABNORMAL_EQ,
	TAG_CORRECTION,
	UNCERTAIN_IDENTIFICATION,
	TIER2_RECONSTRUCTION,
	UNIDENTIFIED,
	/** Same audio as another source file, placed with an "(NN)" suffix (ADR-0019). */
	IDENTICAL_DUPLICATE,
	/** Same path as another file but different audio, placed with "(version NN)" (ADR-0019). */
	ALTERNATIVE_VERSION
}
