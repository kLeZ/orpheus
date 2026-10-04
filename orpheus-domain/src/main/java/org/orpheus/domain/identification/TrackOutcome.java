package org.orpheus.domain.identification;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.Track;

/**
 * Result of the 0, 1, 2, 3 cascade orchestration for a single track (ADR-0017).
 *
 * <p>{@code album} is the Tier 0/1 grouping (null for isolated tracks). {@code release} is the Tier 2
 * result (absent if the fingerprint found nothing). {@code tier3Suggestion} is present only when
 * Tier 2 failed too and the text heuristic produced a suggestion: it is never applied automatically,
 * it goes to the review queue. {@code artistContrastingTag} is present when the Tier 2 release comes
 * from a fingerprint cluster that does not contain the tag artist (ADR-0018 point C): it must be
 * stated in the review item. {@code recording} is the AcoustID recording that led to the release, with
 * score and duration (ADR-0023), if known.
 */
public record TrackOutcome(
                           Track track,
                           @Nullable Album album,
                           Optional<Release> release,
                           boolean forced,
                           Optional<TextClassification> tier3Suggestion,
                           Optional<String> artistContrastingTag,
                           Optional<CandidateRecording> recording) {

	public TrackOutcome(Track track, @Nullable Album album, Optional<Release> release, boolean forced, Optional<TextClassification> tier3Suggestion, Optional<String> artistContrastingTag) {
		this(track, album, release, forced, tier3Suggestion, artistContrastingTag, Optional.empty());
	}

	public TrackOutcome(Track track, @Nullable Album album, Optional<Release> release, boolean forced, Optional<TextClassification> tier3Suggestion) {
		this(track, album, release, forced, tier3Suggestion, Optional.empty());
	}

	/** The same result with another release (release unification, ADR-0020). */
	public TrackOutcome withRelease(Release other) {
		return new TrackOutcome(track, album, Optional.of(other), forced, tier3Suggestion, artistContrastingTag, recording);
	}
}
