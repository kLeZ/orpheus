package org.orpheus.domain.identification;

import java.util.Objects;
import java.util.Optional;
import org.orpheus.domain.model.Track;

/**
 * Result of the multi-track consensus (SPEC.md 4.5, {@link MultiTrackConsensus}) for a single track
 * of the group: {@code assignedRelease} is the one forced by the consensus when {@code forced} is
 * true, otherwise it is simply the result of the individual Tier 2 identification of the track
 * (possibly empty, if no release was found). {@code recording} is the recording of the track that
 * leads to the assigned release, if known (ADR-0023).
 */
public record ConsensusTrackOutcome(Track track,
                                    Optional<Release> assignedRelease,
                                    boolean forced,
                                    Optional<CandidateRecording> recording) {

	public ConsensusTrackOutcome {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(assignedRelease, "assignedRelease");
		Objects.requireNonNull(recording, "recording");
	}

	public ConsensusTrackOutcome(Track track, Optional<Release> assignedRelease, boolean forced) {
		this(track, assignedRelease, forced, Optional.empty());
	}
}
