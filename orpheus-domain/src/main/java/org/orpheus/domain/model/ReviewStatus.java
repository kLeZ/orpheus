package org.orpheus.domain.model;

/** The progress status of an item in the review queue (SPEC.md section 3). */
public enum ReviewStatus {
	PENDING,
	CONFIRMED,
	TO_REPLACE,
	REPLACED,
	REJECTED
}
