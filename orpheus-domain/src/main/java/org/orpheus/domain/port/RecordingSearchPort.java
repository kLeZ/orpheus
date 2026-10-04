package org.orpheus.domain.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Search of MusicBrainz recordings by artist and title (ADR-0023, intervention 4): it only serves to
 * learn the real duration of a song when the AcoustID lookup with the file duration finds nothing
 * because the file is truncated. {@code Optional.empty()} means an error; an empty list is a valid
 * answer.
 */
public interface RecordingSearchPort {

	record FoundRecording(String title, String artistCredit, Optional<Integer> durationSeconds) {

		public FoundRecording {
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(artistCredit, "artistCredit");
			Objects.requireNonNull(durationSeconds, "durationSeconds");
		}
	}

	Optional<List<FoundRecording>> search(String artist, String title);
}
