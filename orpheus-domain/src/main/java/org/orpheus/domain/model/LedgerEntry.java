package org.orpheus.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * An event recorded for a processed track: from where to where, with which action, why, with which
 * confidence (SPEC.md section 3). It is the smallest unit from which the full history of an
 * organized file can be rebuilt, not only its final state (section 8).
 *
 * <p>The confidence follows the same convention as review items (0 excluded, 1 included: ADR-004,
 * SPEC.md section 5), extended here to deterministic actions: copying a file as is is not a bet,
 * and records confidence 1.0.
 */
public record LedgerEntry(
                          Path source,
                          Path destination,
                          LedgerAction action,
                          String reason,
                          double confidence,
                          Instant recordedAt) {

	public LedgerEntry {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(destination, "destination");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(reason, "reason");
		if (reason.isBlank()) {
			throw new IllegalArgumentException("reason cannot be blank");
		}
		if (!(confidence > 0.0 && confidence <= 1.0)) {
			throw new IllegalArgumentException("confidence must be in (0, 1], was " + confidence);
		}
		Objects.requireNonNull(recordedAt, "recordedAt");
	}
}
