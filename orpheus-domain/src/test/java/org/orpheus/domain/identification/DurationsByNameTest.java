package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.RecordingSearchPort.FoundRecording;

// "Beatles/ - Hey Jude.mp3": 380 s out of 431, recognized at 0.99 with the duration of the whole song.
class DurationsByNameTest {

	private static FoundRecording r(String title, String artist, Integer duration) {
		return new FoundRecording(title, artist, Optional.ofNullable(duration));
	}

	@Test
	void durationsOfTheSameSongMostFrequentFirst() {
		var found = List.of(r("Hey Jude", "The Beatles", 431), r("Hey Jude", "The Beatles", 429), r("Hey Jude", "The Beatles", 252), r("Hey Jude", "Wilson Pickett", 245), r("Hey Jude (live)", "The Beatles", 300), r("Hey Jude", "The Beatles", 382), r("Hey Jude", "The Beatles", null));

		assertThat(DurationsByName.candidates(new DurationsByName.Names("Beatles", "Hey Jude"), 380, found)).containsExactly(431, 252);
	}

	@Test
	void namesFromTheTagOrTheFolder() {
		var m = new TrackMetadata(null, "Hey Jude", null, null, null, null, null, ExternalIds.UNKNOWN);
		Path p = Path.of("/music/Beatles/ - Hey Jude.mp3");
		Track t = new Track(p, m, new Provenance(p, ProvenanceState.INTACT), "Beatles");

		assertThat(DurationsByName.names(t)).contains(new DurationsByName.Names("Beatles", "Hey Jude"));
		assertThat(DurationsByName.names(new Track(p, m, new Provenance(p, ProvenanceState.INTACT)))).isEmpty();
	}
}
