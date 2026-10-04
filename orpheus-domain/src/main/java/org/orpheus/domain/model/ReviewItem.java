package org.orpheus.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One unit of the review queue: a track, a problem category, a readable reason, a status and the raw
 * signal specific to the category (SPEC.md sections 3 and 5). Usually a single signal; the empty list
 * is the case with no reliable signal at all (e.g. {@link ReviewCategory#UNIDENTIFIED}). The
 * priority formula that derives a comparable value from it (ADR-004) lives elsewhere, not in this
 * model.
 */
public record ReviewItem(
                         Track track,
                         ReviewCategory category,
                         String reason,
                         ReviewStatus status,
                         List<RawSignal> signals) {

	public ReviewItem {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(reason, "reason");
		if (reason.isBlank()) {
			throw new IllegalArgumentException("reason cannot be blank");
		}
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(signals, "signals");
		signals = List.copyOf(signals);
	}
}
