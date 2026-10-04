package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses the winning release among the candidates a recording already identified by fingerprint
 * appears on (SPEC.md section 4.4, ADR-001): first it filters by type (an Album always beats EP,
 * Single or Other, unless none of the candidates is an Album), then among the remaining releases the
 * one with the chronologically oldest release date wins, according to
 * {@link ReleaseDate#compareTo(ReleaseDate)}.
 */
public final class ReleaseSelector {

	private ReleaseSelector() {
	}

	public static Optional<Release> choose(List<Release> candidates) {
		Objects.requireNonNull(candidates, "candidates");
		if (candidates.isEmpty()) {
			return Optional.empty();
		}

		List<Release> albums = candidates.stream().filter(e -> e.type() == ReleaseType.ALBUM).toList();
		List<Release> allowed = albums.isEmpty() ? candidates : albums;

		Release winner = allowed.get(0);
		for (Release candidate : allowed) {
			if (candidate.releaseDate().compareTo(winner.releaseDate()) < 0) {
				winner = candidate;
			}
		}
		return Optional.of(winner);
	}
}
