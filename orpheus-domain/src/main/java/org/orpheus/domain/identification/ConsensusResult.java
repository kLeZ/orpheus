package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of the multi-track consensus for a whole group (SPEC.md 4.5,
 * {@link MultiTrackConsensus#compute}): {@code winningRelease} is present only if at least 65% of the
 * tracks of the group converge on the same release; otherwise it is empty and no track in
 * {@code tracks} is forced.
 */
public record ConsensusResult(Optional<Release> winningRelease, List<ConsensusTrackOutcome> tracks) {

	public ConsensusResult {
		Objects.requireNonNull(winningRelease, "winningRelease");
		Objects.requireNonNull(tracks, "tracks");
		tracks = List.copyOf(tracks);
	}
}
