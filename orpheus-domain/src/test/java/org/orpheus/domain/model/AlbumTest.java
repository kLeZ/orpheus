package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlbumTest {

	private static Track track(String fileName) {
		var metadata = new TrackMetadata(
		        "Artist", "Title", "Album", "Artist", 1998, 1, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/source/" + fileName), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/" + fileName), metadata, provenance);
	}

	@Test
	void buildsAPartialAlbumWithTheKnownTracks() {
		var album = new Album(
		        "Album", "Artist", 1998, AlbumCompleteness.PARTIAL, List.of(track("t1.mp3"), track("t2.mp3")));

		assertThat(album.completeness()).isEqualTo(AlbumCompleteness.PARTIAL);
		assertThat(album.tracks()).hasSize(2);
	}

	@Test
	void theTrackListIsImmutable() {
		var modifiableTracks = new java.util.ArrayList<Track>();
		modifiableTracks.add(track("t1.mp3"));
		var album = new Album("Album", null, null, AlbumCompleteness.COMPLETE, modifiableTracks);

		modifiableTracks.add(track("t2.mp3"));

		assertThat(album.tracks()).hasSize(1);
		assertThatThrownBy(() -> album.tracks().add(track("t3.mp3")))
		                                                             .isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsAnAlbumWithoutTracks() {
		assertThatThrownBy(
		        () -> new Album("Album", null, null, AlbumCompleteness.COMPLETE, List.of()))
		                                                                                    .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankTitle() {
		assertThatThrownBy(
		        () -> new Album(
		                "   ", null, null, AlbumCompleteness.COMPLETE, List.of(track("t1.mp3"))))
		                                                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsATier0Tier1GroupingNotYetCheckedAgainstAnExternalSource() {
		var album = new Album(
		        "Album", "Artist", 1998, AlbumCompleteness.UNVERIFIED, List.of(track("t1.mp3")));

		assertThat(album.completeness()).isEqualTo(AlbumCompleteness.UNVERIFIED);
	}

	@Test
	void rejectsANullCompleteness() {
		assertThatThrownBy(
		        () -> new Album("Album", null, null, null, List.of(track("t1.mp3"))))
		                                                                             .isInstanceOf(NullPointerException.class);
	}
}
