package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

class DestinationPathTest {

	private static Track track(String file, String artist, String title, String album, String albumArtist, Integer year, Integer trackNumber, Integer discNumber) {
		TrackMetadata metadata = new TrackMetadata(artist, title, album, albumArtist, year, trackNumber, discNumber, ExternalIds.unknown());
		return new Track(Path.of("/source/" + file), metadata, new Provenance(Path.of("/source/" + file), ProvenanceState.INTACT));
	}

	@Test
	void aNormalAlbumWithASingleDisc() {
		Track track = track("02.flac", "Radiohead", "Karma Police", "OK Computer", "Radiohead", 1997, 2, null);
		Album album = new Album("OK Computer", "Radiohead", 1997, AlbumCompleteness.COMPLETE, List.of(track));

		Path result = DestinationPath.compute(track, album);

		assertThat(result).isEqualTo(Path.of("Radiohead", "1997 - OK Computer", "02 - Karma Police.flac"));
	}

	@Test
	void anAlbumWithSeveralDiscsAddsADiscFolder() {
		Track track1 = track("t1.mp3", "Pink Floyd", "In The Flesh", "The Wall", "Pink Floyd", 1979, 1, 1);
		Track track2 = track("t2.mp3", "Pink Floyd", "Hey You", "The Wall", "Pink Floyd", 1979, 1, 2);
		Album album = new Album("The Wall", "Pink Floyd", 1979, AlbumCompleteness.COMPLETE, List.of(track1, track2));

		Path result1 = DestinationPath.compute(track1, album);
		Path result2 = DestinationPath.compute(track2, album);

		assertThat(result1)
		                   .isEqualTo(Path.of("Pink Floyd", "1979 - The Wall", "Disc 1", "01 - In The Flesh.mp3"));
		assertThat(result2).isEqualTo(Path.of("Pink Floyd", "1979 - The Wall", "Disc 2", "01 - Hey You.mp3"));
	}

	@Test
	void variousArtistsIncludesTheTrackArtistInTheFileName() {
		Track track1 = track("t1.ogg", "Blur", "Song 2", "Trainspotting", "Various Artists", 1996, 3, null);
		Track track2 = track("t2.ogg", "Iggy Pop", "Lust For Life", "Trainspotting", "Various Artists", 1996, 1, null);
		Album album = new Album("Trainspotting", "Various Artists", 1996, AlbumCompleteness.UNVERIFIED, List.of(track1, track2));

		Path result = DestinationPath.compute(track1, album);

		assertThat(result).isEqualTo(
		        Path.of("Various Artists", "1996 - Trainspotting", "03 - Blur - Song 2.ogg"));
	}

	@Test
	void anIsolatedTrackWithoutAlbumStaysTraceableUnderAKnownArtist() {
		Track track = track("song.mp3", "Sigur Ros", "Svefn-g-englar", null, null, null, null, null);

		Path result = DestinationPath.compute(track, null);

		assertThat(result).isEqualTo(Path.of("Sigur Ros", "Unidentified", "Svefn-g-englar.mp3"));
	}

	@Test
	void anIsolatedTrackWithoutArtistOrAlbumUsesAnExplicitContainer() {
		Track track = track("mystery.mp3", null, null, null, null, null, null, null);

		Path result = DestinationPath.compute(track, null);

		assertThat(result)
		                  .isEqualTo(Path.of("Unknown Artist", "Unidentified", "Unknown Track.mp3"));
	}

	// The placeholder names are configurable (orpheus.placeholders.*).
	@Test
	void thePlaceholderNamesCanBeConfigured() {
		Track track = track("mystery.mp3", null, null, null, null, null, null, null);
		PlaceholderNames italian = new PlaceholderNames("Artista Sconosciuto", "Anno sconosciuto", "Traccia sconosciuta", "Non Identificato", "Disco", "versione");

		Path result = DestinationPath.compute(track, null, CanonicalArtistNames.empty(), italian);

		assertThat(result).isEqualTo(Path.of("Artista Sconosciuto", "Non Identificato", "Traccia sconosciuta.mp3"));
	}

	@Test
	void aMissingYearUsesAPlaceholderInsteadOfAFalseYear() {
		Track track = track("01.flac", "Local Theme", "Boh", "Family Collection", "Local Theme", null, 1, null);
		Album album = new Album("Family Collection", "Local Theme", null, AlbumCompleteness.UNVERIFIED, List.of(track));

		Path result = DestinationPath.compute(track, album);

		assertThat(result).isEqualTo(
		        Path.of("Local Theme", "Unknown Year - Family Collection", "01 - Boh.flac"));
	}

	@Test
	void charactersInvalidForTheFileSystemAreSanitized() {
		Track track = track("07.mp3", "An/Artist", "Title: \"odd\"? <really>|yes", "Album/Odd", "An/Artist", 2001, 7, null);
		Album album = new Album("Album/Odd", "An/Artist", 2001, AlbumCompleteness.COMPLETE, List.of(track));

		Path result = DestinationPath.compute(track, album);

		assertThat(result).isEqualTo(
		        Path.of("An-Artist", "2001 - Album-Odd", "07 - Title- -odd-- -really--yes.mp3"));
	}

	private static Track withArtistFromPath(Track t, String artistFromPath) {
		return new Track(t.physicalPath(), t.metadata(), t.provenance(), artistFromPath);
	}

	// Skunk Anansie on the real run: files downloaded without any tag, in a folder named after the band.
	@Test
	void withoutArtistTagTheTopLevelFolderIsUsed() {
		Track t = withArtistFromPath(track("Skunk Anansie/ - Because of you.mp3", null, "Because of you", null, null, null, null, null), "Skunk Anansie");

		assertThat(DestinationPath.compute(t, null)).isEqualTo(Path.of("Skunk Anansie", "Unidentified", "Because of you.mp3"));
	}

	@Test
	void aFolderThatIsAlreadyTheAlbumTitleDoesNotAlsoBecomeTheArtist() {
		Track t = withArtistFromPath(track("Nouveau titre/01 - Plage 1.mp3", null, "Plage 1", null, null, null, 1, null), "Nouveau titre");
		Album album = new Album("Nouveau titre", null, null, AlbumCompleteness.UNVERIFIED, List.of(t));

		assertThat(DestinationPath.compute(t, album)).isEqualTo(Path.of("Unknown Artist", "Unknown Year - Nouveau titre", "01 - Plage 1.mp3"));
	}

	private static Release release(String title, String credit, int year, Integer disc, Integer number, String trackTitle) {
		return new Release("rel", ReleaseType.ALBUM, ReleaseDate.yearOnly(year), List.of(credit), false, new ReleaseInfo(title, credit, "rec", disc, number, trackTitle));
	}

	// "Bon Jovi/ - Bed of Roses.mp3" on the real run: from the artist folder to the original album.
	@Test
	void anIdentifiedTrackGoesToItsReleaseWithTracklistNumberAndTitle() {
		Track t = track("Bon Jovi/ - Bed of Roses.mp3", "Bon Jovi", "Bed of Roses", null, null, null, null, null);

		Path p = DestinationPath.fromRelease(t, release("Keep the Faith", "Bon Jovi", 1992, 1, 7, "Bed of Roses"), false, null, CanonicalArtistNames.empty());

		assertThat(p).isEqualTo(Path.of("Bon Jovi", "1992 - Keep the Faith", "07 - Bed of Roses.mp3"));
	}

	@Test
	void releaseCreditWithTypographicCharactersAndDiscIfMultiDisc() {
		Track t = track("Blink 182/x.mp3", "Blink 182", "Dammit", null, null, null, null, null);

		Path p = DestinationPath.fromRelease(t, release("Dude Ranch", "blink\u2010182", 1997, 2, 4, "Dammit (Growing Up)"), true, null, CanonicalArtistNames.empty());

		assertThat(p).isEqualTo(Path.of("blink-182", "1997 - Dude Ranch", "Disc 2", "04 - Dammit (Growing Up).mp3"));
	}

	@Test
	void unidentifiedTracksUseTheCanonicalArtistName() {
		CanonicalArtistNames canonical = CanonicalArtistNames.empty();
		canonical.register("ACDC", "AC/DC");
		canonical.register("Guns N Roses", "Carl Douglas");
		Track t = track("ACDC/ - Meltdown.mp3", "ACDC", "Meltdown", null, null, null, null, null);

		assertThat(DestinationPath.compute(t, null, canonical)).isEqualTo(Path.of("AC-DC", "Unidentified", "Meltdown.mp3"));
		assertThat(canonical.canonical("Guns N Roses")).isEqualTo("Guns N Roses");
	}

	@Test
	void capitalizationVariantsOfTheSameArtistEndUpInTheMostFrequentFolder() {
		CanonicalArtistNames canonical = CanonicalArtistNames.empty();
		canonical.registerLocal("Pain Of Salvation");
		canonical.registerLocal("Pain Of Salvation");
		canonical.registerLocal("Pain of Salvation");

		assertThat(canonical.canonical("Pain of Salvation")).isEqualTo("Pain Of Salvation");
	}

	// "Alterbridge/ - Open Your eyes.mp3" on the real run: on MusicBrainz it appears only on a compilation.
	@Test
	void aSingleFoundOnlyOnACompilationStaysUnderItsArtist() {
		Track t = track("Alterbridge/ - Open Your eyes.mp3", "Alterbridge", "Open Your Eyes", null, null, null, null, null);
		Release compilation = new Release("va", ReleaseType.ALBUM, ReleaseDate.yearOnly(2004), List.of("Various Artists"), true, new ReleaseInfo("Rock Hits 2004", "Various Artists", "rec", 1, 5, "Open Your Eyes"));

		assertThat(DestinationPath.fromRelease(t, compilation, false, null, CanonicalArtistNames.empty(), false)).isEqualTo(Path.of("Alterbridge", "2004 - Rock Hits 2004", "05 - Open Your Eyes.mp3"));
		assertThat(DestinationPath.fromRelease(t, compilation, false, null, CanonicalArtistNames.empty(), true)).isEqualTo(Path.of("Various Artists", "2004 - Rock Hits 2004", "05 - Alterbridge - Open Your Eyes.mp3"));
	}

	// "Bat Head Soup": the file name uses the performer of the tracklist, like the ARTIST tag.
	@Test
	void inACompilationTheFileNameUsesTheTracklistPerformer() {
		Track t = track("Dio & Yngwie Malmsteen/ - Mr. Crowley.mp3", "R. J. Dio & Yngwie Malmsteen", "Mr. Crowley", null, null, null, null, null);
		ReleaseInfo info = new ReleaseInfo("Bat Head Soup: Tribute to Ozzy", "Various Artists", "rec", 1, 1, "Mr. Crowley", null, "Yngwie Malmsteen", List.of(), List.of());
		Release compilation = new Release("bhs", ReleaseType.ALBUM, ReleaseDate.yearOnly(2000), List.of("Various Artists"), true, info);

		assertThat(DestinationPath.fromRelease(t, compilation, false, null, CanonicalArtistNames.empty(), true)).isEqualTo(Path.of("Various Artists", "2000 - Bat Head Soup- Tribute to Ozzy", "01 - Yngwie Malmsteen - Mr. Crowley.mp3"));
	}

	@Test
	void aYearRepeatedInTheTitleAndDoubleSpacesAreRemoved() {
		Track t = track("The Doors/(1968) Waiting For The Sun/09.mp3", "The Doors", "We Could Be So Good Together", "Waiting For The Sun (1968)", null, 1968, 9, null);
		Album album = new Album("Waiting For The Sun (1968)", "The Doors", 1968, AlbumCompleteness.UNVERIFIED, List.of(t));
		Track u = track("Pain/x.mp3", "Pain Of Salvation", "Linoleum", "Road Salt One (Limited  Edition)", null, 2010, 9, null);
		Album album2 = new Album("Road Salt One (Limited  Edition)", "Pain Of Salvation", 2010, AlbumCompleteness.UNVERIFIED, List.of(u));

		assertThat(DestinationPath.compute(t, album)).isEqualTo(Path.of("The Doors", "1968 - Waiting For The Sun", "09 - We Could Be So Good Together.mp3"));
		assertThat(DestinationPath.compute(u, album2).getName(1).toString())
		                                                                    .isEqualTo("2010 - Road Salt One (Limited Edition)");
	}
}
