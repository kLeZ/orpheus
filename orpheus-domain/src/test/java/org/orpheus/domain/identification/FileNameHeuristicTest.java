package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

class FileNameHeuristicTest {

	@Test
	void artistTitleWithSpaces() {
		Optional<TextClassification> r = classify("Artist - Title.mp3");
		assertThat(r).isPresent();
		assertThat(r.get().artist()).hasValue("Artist");
		assertThat(r.get().title()).hasValue("Title");
		assertThat(r.get().confidence()).isEqualTo(0.8);
	}

	@Test
	void artistTitleWithoutSpaces() {
		Optional<TextClassification> r = classify("Artist-Title.mp3");
		assertThat(r).isPresent();
		assertThat(r.get().artist()).hasValue("Artist");
		assertThat(r.get().title()).hasValue("Title");
	}

	@Test
	void trackNumberArtistTitle() {
		Optional<TextClassification> r = classify("01 - Artist - Title.mp3");
		assertThat(r).isPresent();
		assertThat(r.get().artist()).hasValue("Artist");
		assertThat(r.get().title()).hasValue("Title");
	}

	@Test
	void aTitleAloneAfterANumberStaysEmpty() {
		// After removing the track number only a word without a separator is left; the heuristic is
		// conservative and does not assume it is the title.
		assertThat(classify("03 - Title.mp3")).isEmpty();
	}

	@Test
	void aNameWithoutARecognizablePattern() {
		assertThat(classify("alone.mp3")).isEmpty();
		assertThat(classify("01.mp3")).isEmpty();
		assertThat(classify("song.mp3")).isEmpty();
	}

	@Test
	void artistTitleWithRemix() {
		Optional<TextClassification> r = classify("Artist - Title - Remix.mp3");
		assertThat(r).isPresent();
		assertThat(r.get().artist()).hasValue("Artist");
		assertThat(r.get().title()).hasValue("Title - Remix");
	}

	// Real run of 2026-09-26: "Lagwagon/ - Take a look around - Punk cover.mp3" gave the artist "- Take
	// a look around". With the artist known from the folder, all the rest is title.
	@Test
	void withTheArtistKnownFromTheFolderTheFirstSegmentIsNotAnArtist() {
		Path path = Path.of("/music/Lagwagon/ - Take a look around - Punk cover.mp3");
		Track track = new Track(
		        path, new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.unknown()), new Provenance(path, ProvenanceState.INTACT), "Lagwagon");

		Optional<TextClassification> r = FileNameHeuristic.classify(track);

		assertThat(r).isPresent();
		assertThat(r.get().artist()).isEmpty();
		assertThat(r.get().title()).hasValue("Take a look around - Punk cover");
	}

	@Test
	void aLeadingDashWithoutANumberGivesATitle() {
		Optional<TextClassification> r = classify(" - Wonderwall.mp3");
		assertThat(r).isPresent();
		assertThat(r.get().title()).hasValue("Wonderwall");
		assertThat(r.get().confidence()).isEqualTo(0.5);
	}

	private static Optional<TextClassification> classify(String fileName) {
		Path path = Path.of("/tmp/test").resolve(fileName);
		Track track = new Track(
		        path, new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.unknown()), new Provenance(path, ProvenanceState.INTACT));
		return FileNameHeuristic.classify(track);
	}
}
