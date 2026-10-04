package org.orpheus.domain.model;

/** Whether and how a track was changed from the original source file (SPEC.md section 3). */
public enum ProvenanceState {
	INTACT,
	TAGS_CORRECTED,
	AUDIO_REPLACED
}
