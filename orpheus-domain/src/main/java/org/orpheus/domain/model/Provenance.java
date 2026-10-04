package org.orpheus.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a track comes from and whether and how it was changed from the original (SPEC.md section 3).
 * A snapshot of the current state, not the full history: that is the job of {@link LedgerEntry}.
 */
public record Provenance(Path source, ProvenanceState state) {

	public Provenance {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(state, "state");
	}
}
