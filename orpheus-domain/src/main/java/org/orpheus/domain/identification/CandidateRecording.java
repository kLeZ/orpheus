package org.orpheus.domain.identification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A MusicBrainz recording linked to the fingerprint of a track (ADR-0018), with the metadata needed to
 * discard the unrelated recordings the community linked to the same AcoustID cluster. {@code title}
 * and {@code artists} can be missing when the database does not provide them;
 * {@code durationSeconds} is the duration of the recording, not of the file. {@code score} is the
 * score of the AcoustID result that brought the recording (ADR-0023), absent if unknown;
 * {@code associations} is how many times the recording was associated with the fingerprint on
 * AcoustID ("sources").
 */
public record CandidateRecording(String recordingId,
                                 @Nullable String title,
                                 List<String> artists,
                                 Optional<Integer> durationSeconds,
                                 Optional<Double> score,
                                 Optional<Integer> associations) {

	public CandidateRecording {
		Objects.requireNonNull(recordingId, "recordingId");
		if (recordingId.isBlank()) {
			throw new IllegalArgumentException("recordingId cannot be blank");
		}
		Objects.requireNonNull(artists, "artists");
		Objects.requireNonNull(durationSeconds, "durationSeconds");
		Objects.requireNonNull(score, "score");
		Objects.requireNonNull(associations, "associations");
		artists = List.copyOf(artists);
	}

	public CandidateRecording(String recordingId, @Nullable String title, List<String> artists, Optional<Integer> durationSeconds, Optional<Double> score) {
		this(recordingId, title, artists, durationSeconds, score, Optional.empty());
	}

	public CandidateRecording(String recordingId, @Nullable String title, List<String> artists, Optional<Integer> durationSeconds) {
		this(recordingId, title, artists, durationSeconds, Optional.empty());
	}

	/** A candidate known only by id: no artist or duration filter can confirm it. */
	public static CandidateRecording idOnly(String recordingId) {
		return new CandidateRecording(recordingId, null, List.of(), Optional.empty());
	}
}
