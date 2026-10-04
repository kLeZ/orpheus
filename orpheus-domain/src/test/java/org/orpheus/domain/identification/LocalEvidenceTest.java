package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

// Cases from the real run of 2026-09-26 (docs/TODO.md, "Damage observed").
class LocalEvidenceTest {

	private static final Path ROOT = Path.of("/music");

	private static Track track(String relative, @Nullable String artist, @Nullable String title, @Nullable String album, @Nullable String albumArtist, @Nullable Integer year, @Nullable Integer number) {
		Path file = ROOT.resolve(relative);
		var metadata = new TrackMetadata(artist, title, album, albumArtist, year, number, null, ExternalIds.UNKNOWN);
		return new Track(file, metadata, new Provenance(file, ProvenanceState.INTACT));
	}

	private static Track one(Track t) {
		return LocalEvidence.apply(List.of(t), ROOT).get(0);
	}

	@Test
	void missingTitleAndNumberFromTheFileName() {
		var t = one(track("Linkin Park/05 - Crawling.mp3", "Linkin Park", null, null, null, null, null));
		assertThat(t.metadata().title()).isEqualTo("Crawling");
		assertThat(t.metadata().trackNumber()).isEqualTo(5);
	}

	@Test
	void aValidTagWinsOverTheFileName() {
		var t = one(track("Nickelback/07 - too bad.mp3", "Nickelback", "Too Bad", null, null, null, 7));
		assertThat(t.metadata().title()).isEqualTo("Too Bad");
	}

	@Test
	void missingYearFromTheFolderName() {
		assertThat(one(track("Deep Purple/Deep Purple - 1971 - Fireball/01 - Fireball.mp3", "Deep Purple", "Fireball", "Fireball", null, null, 1)).metadata()
		                                                                                                                                          .year()).isEqualTo(1971);
		assertThat(one(track("Dire Straits/(1998) Sultans Of Swing (Limited Ed)/01.mp3", "Dire Straits", "X", "Y", null, null, 1)).metadata()
		                                                                                                                          .year()).isEqualTo(1998);
		assertThat(one(track("Myr/2013 - Days Of Convergence/01.flac", "Myr", "X", "Y", null, null, 1)).metadata()
		                                                                                               .year()).isEqualTo(2013);
	}

	@Test
	void withTwoValidYearsTheOlderWinsAndANumericTitleIsNotAYear() {
		// Queen/01 Queen (1973) with tag 1994 (remaster); Pain of Salvation with tag 2000 and folder
		// "2001 - The Perfect Element Part 1".
		assertThat(one(track("Queen/01 Queen (1973)/01.mp3", "Queen", "X", "Queen", null, 1994, 1)).metadata().year())
		                                                                                                              .isEqualTo(1973);
		assertThat(one(track("Pain Of Salvation/2001 - The Perfect Element Part 1/01.mp3", "Pain Of Salvation", "X", "Y", null, 2000, 1)).metadata()
		                                                                                                                                 .year()).isEqualTo(2000);
		assertThat(LocalEvidence.yearFromFolder("Prince 1999 Deluxe")).isNull();
	}

	@Test
	void anImplausibleYearIsTreatedAsAbsent() {
		assertThat(one(track("Deep Purple/Made in Japan/02.mp3", "Deep Purple", "X", "Y", null, 197, 2)).metadata()
		                                                                                                .year()).isNull();
	}

	@Test
	void placeholdersAreTreatedAsAbsent() {
		var t = one(track("Linkin Park/06 - What I've done.wma", "Linkin Park", "Traccia sconosciuta", "Album sconosciuto", "Artista sconosciuto", null, 6));
		assertThat(t.metadata().albumArtist()).isNull();
		assertThat(t.metadata().album()).isNull();
		assertThat(t.metadata().title()).isEqualTo("What I've done");
	}

	@Test
	void variousArtistsStaysAValidAlbumArtist() {
		var t = one(track("Compilations/Crossing All Over/01.mp3", "Guano Apes", "Open Your Eyes", "Crossing All Over", "Various Artists", 1997, 1));
		assertThat(t.metadata().albumArtist()).isEqualTo("Various Artists");
	}

	@Test
	void aNumberRepeatedInTheSameFolderIsRemoved() {
		var tracks = LocalEvidence.apply(
		        List.of(track("Linkin Park/32 - Breaking The Habit.mp3", "Linkin Park", null, null, null, null, 32), track("Linkin Park/32 - Don't Stay.mp3", "Linkin Park", null, null, null, null, 32), track("Linkin Park/08 - In The End.mp3", "Linkin Park", null, null, null, null, 8)), ROOT);

		assertThat(tracks).extracting(t -> t.metadata().trackNumber()).containsExactly(null, null, 8);
		assertThat(tracks.get(0).metadata().title()).isEqualTo("Breaking The Habit");
	}

	// Root of ~/Musica: Sonata Arctica track 6 without an album and Elio track 6 of "L'album Biango".
	@Test
	void theSameNumberInDifferentAlbumsOfTheSameFolderIsNotRemoved() {
		var tracks = LocalEvidence.apply(
		        List.of(track("06 - 8th Commandment (live).mp3", "Sonata Arctica", null, null, null, null, 6), track("06. Enlarge (Your penis).mp3", "Elio e le Storie Tese", "Enlarge(your penis)", "L'album Biango", null, 2013, 6)), ROOT);

		assertThat(tracks).extracting(t -> t.metadata().trackNumber()).containsExactly(6, 6);
	}

	@Test
	void artistFromPathOnlyUnderATopLevelFolder() {
		assertThat(one(track("Skunk Anansie/ - Because of you.mp3", null, null, null, null, null, null)).artistFromPath()).isEqualTo("Skunk Anansie");
		assertThat(one(track("02 - Weballergy.mp3", "Sonata Arctica", null, null, null, null, null)).artistFromPath()).isNull();
		assertThat(one(track("Unknown Artist/Unknown Album/01 - Track 01.mp3", "Unknown Artist", null, null, null, null, null)).artistFromPath()).isNull();
	}

	// ADR-0021: the FILL rules look at the original tag, not at the value completed from the file name.
	@Test
	void theOriginalMetadataStaysDistinctFromTheCompletedOne() {
		var t = one(track("Linkin Park/05 - Crawling.mp3", "Linkin Park", null, null, "Artista sconosciuto", null, null));

		assertThat(t.metadata().title()).isEqualTo("Crawling");
		assertThat(t.originalMetadata().title()).isNull();
		assertThat(t.originalMetadata().albumArtist()).isEqualTo("Artista sconosciuto");
		assertThat(t.withMetadata(t.metadata()).originalMetadata().title()).isNull();
	}
}
