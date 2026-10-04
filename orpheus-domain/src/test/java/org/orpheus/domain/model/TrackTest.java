package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrackTest {

	private static final TrackMetadata MINIMAL_METADATA = new TrackMetadata(
	        "Artist", "Title", "Album", "Artist", 1998, 1, null, ExternalIds.UNKNOWN);

	private static final Provenance INTACT_PROVENANCE = new Provenance(Path.of("/source/track.mp3"), ProvenanceState.INTACT);

	@Test
	void buildsACompleteTrack() {
		var track = new Track(Path.of("/dest/track.mp3"), MINIMAL_METADATA, INTACT_PROVENANCE);

		assertThat(track.physicalPath()).isEqualTo(Path.of("/dest/track.mp3"));
		assertThat(track.metadata()).isEqualTo(MINIMAL_METADATA);
		assertThat(track.provenance()).isEqualTo(INTACT_PROVENANCE);
	}

	@Test
	void anIsolatedTrackWithoutAnyAlbumSimplyHasNullAlbumFields() {
		var metadataWithoutAlbum = new TrackMetadata(
		        null, "Isolated title", null, null, null, null, null, ExternalIds.UNKNOWN);

		var track = new Track(Path.of("/dest/isolated.flac"), metadataWithoutAlbum, INTACT_PROVENANCE);

		assertThat(track.metadata().album()).isNull();
	}

	@Test
	void rejectsANullPhysicalPath() {
		assertThatThrownBy(() -> new Track(null, MINIMAL_METADATA, INTACT_PROVENANCE))
		                                                                              .isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsNullMetadata() {
		assertThatThrownBy(() -> new Track(Path.of("/dest/track.mp3"), null, INTACT_PROVENANCE))
		                                                                                        .isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsANullProvenance() {
		assertThatThrownBy(() -> new Track(Path.of("/dest/track.mp3"), MINIMAL_METADATA, null))
		                                                                                       .isInstanceOf(NullPointerException.class);
	}
}
