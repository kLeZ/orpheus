package org.orpheus.domain.model;

/** The action taken on a track in a {@link LedgerEntry} (SPEC.md section 3). */
public enum LedgerAction {
	IMPORTED_ENRICHED,
	IMPORTED_AS_IS,
	DISCARDED_DUPLICATE,
	RECONCILED,
	RECONSTRUCTED
}
