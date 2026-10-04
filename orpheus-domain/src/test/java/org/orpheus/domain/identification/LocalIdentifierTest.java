package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

class LocalIdentifierTest {

	private static Track track(
	                           String sourceFolder, String fileName, @Nullable String artist, @Nullable String title, @Nullable String album, @Nullable String albumArtist, @Nullable Integer year) {
		var metadata = new TrackMetadata(
		        artist, title, album, albumArtist, year, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of(sourceFolder, fileName), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/" + fileName), metadata, provenance);
	}

	@Test
	void noTrackProducesAnEmptyResultWithoutExceptions() {
		var result = LocalIdentifier.identify(List.of());

		assertThat(result.albums()).isEmpty();
		assertThat(result.isolatedTracks()).isEmpty();
	}

	@Test
	void rejectsANullCollection() {
		assertThatThrownBy(() -> LocalIdentifier.identify(null))
		                                                        .isInstanceOf(NullPointerException.class);
	}

	@Test
	void tier0GroupsByFolderASubsetOfRealTracksWithoutAlbumTag() {
		// The base case: the user kept only two tracks of a record, the album tag was never written.
		// Mixed formats (flac/ogg), not only mp3 (DESIGN.md).
		var t1 = track("/music/Big Record", "02 track.flac", "Artist", "Title 2", null, null, null);
		var t2 = track("/music/Big Record", "07 track.ogg", "Artist", "Title 7", null, null, null);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(1);
		Album album = result.albums().get(0);
		assertThat(album.title()).isEqualTo("Big Record");
		assertThat(album.completeness()).isEqualTo(AlbumCompleteness.UNVERIFIED);
		assertThat(album.tracks()).containsExactlyInAnyOrder(t1, t2);
	}

	@Test
	void theAlbumYearIsTheMajorityNotTheFirstFile() {
		var t0 = track("/music/Black Sabbath/Sabotage", " - 03.mp3", "Black Sabbath", null, "Sabotage", null, 1973);
		var t1 = track("/music/Black Sabbath/Sabotage", "01 - Hole in the Sky.mp3", "Black Sabbath", "Hole in the Sky", "Sabotage", null, 1975);
		var t2 = track("/music/Black Sabbath/Sabotage", "02 - Don't Start.mp3", "Black Sabbath", "Don't Start", "Sabotage", null, 1975);

		var result = LocalIdentifier.identify(List.of(t0, t1, t2));

		assertThat(result.albums()).singleElement().extracting(Album::year).isEqualTo(1975);
	}

	@Test
	void onATieOfTracksTheOldestYearWins() {
		var t1 = track("/music/Queen/Queen", "01.mp3", "Queen", "Keep Yourself Alive", "Queen", null, 1994);
		var t2 = track("/music/Queen/Queen", "02.mp3", "Queen", "Doing All Right", "Queen", null, 1973);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.albums()).singleElement().extracting(Album::year).isEqualTo(1973);
	}

	@Test
	void aPlaceholderAlbumTagIsTreatedAsMissingAndFallsBackOnTheFolder() {
		var t1 = track("/music/Folder X", "01.mp3", "Artist", "Track 1", "Unknown Album", "", 0);
		var t2 = track("/music/Folder X", "02.mp3", "Artist", "Track 2", "unknown album", "", 0);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).title()).isEqualTo("Folder X");
	}

	@Test
	void aSingleTrackInAnUntaggedFolderStaysIsolated() {
		var lonely = track("/music/Lonely Folder", "01.mp3", "Artist", "Title", null, null, null);

		var result = LocalIdentifier.identify(List.of(lonely));

		assertThat(result.albums()).isEmpty();
		assertThat(result.isolatedTracks()).containsExactly(lonely);
	}

	@Test
	void aTrackWithoutSourceFolderStaysIsolatedWithoutExceptions() {
		var metadata = new TrackMetadata(
		        "Artist", "Title", null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("isolated.mp3"), ProvenanceState.INTACT);
		var track = new Track(Path.of("/dest/isolated.mp3"), metadata, provenance);

		var result = LocalIdentifier.identify(List.of(track));

		assertThat(result.albums()).isEmpty();
		assertThat(result.isolatedTracks()).containsExactly(track);
	}

	@Test
	void tier1ReconcilesTheSameAlbumSpreadOverSeveralSourceFolders() {
		var t1 = track("/import/cd1", "01.mp3", "Toto", "Rosanna", "Toto IV", "Toto", 1982);
		var t2 = track("/import/cd1", "02.flac", "Toto", "Africa", "Toto IV", "Toto", 1982);
		// Same release but copied from another source folder, album tag with different capitalization.
		var t3 = track("/download/misc", "03.ogg", "Toto", "I Won't Hold You Back", "TOTO IV", "Toto", null);

		var result = LocalIdentifier.identify(List.of(t1, t2, t3));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(1);
		Album album = result.albums().get(0);
		assertThat(album.title()).isEqualTo("Toto IV");
		assertThat(album.albumArtist()).isEqualTo("Toto");
		assertThat(album.completeness()).isEqualTo(AlbumCompleteness.UNVERIFIED);
		assertThat(album.tracks()).containsExactlyInAnyOrder(t1, t2, t3);
	}

	@Test
	void localVariousArtistsTriggersWithAtLeastTwoDifferentNonEmptyTrackArtists() {
		var t1 = track("/music/Summer Compilation", "01.mp3", "Artist A", "Song 1", "Summer Compilation", null, 2001);
		var t2 = track("/music/Summer Compilation", "02.mp3", "Artist B", "Song 2", "Summer Compilation", null, 2001);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.albums()).hasSize(1);
		Album album = result.albums().get(0);
		assertThat(album.albumArtist()).isEqualTo("Various Artists");
		assertThat(album.tracks()).hasSize(2);
	}

	@Test
	void localVariousArtistsDoesNotTriggerWithASingleTrackArtist() {
		var t1 = track("/music/Album Singolo", "01.mp3", "Artist A", "Song 1", "Single Album", null, 2001);
		var t2 = track("/music/Album Singolo", "02.mp3", "Artist A", "Song 2", "Single Album", null, 2001);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.albums()).hasSize(1);
		Album album = result.albums().get(0);
		assertThat(album.albumArtist()).isEqualTo("Artist A");
		assertThat(album.albumArtist()).isNotEqualTo("Various Artists");
	}

	@Test
	void localVariousArtistsDoesNotFragmentWhenTheAlbumArtistTagIsMissingEntirely() {
		// Same album tag, no album artist tag written: the rule must produce a single Various Artists
		// album, not two separate albums for the two track artists.
		var t1 = track("/music/Compilation", "01.mp3", "Artist A", "Song 1", "Compilation", null, null);
		var t2 = track("/music/Compilation", "02.mp3", "Artist B", "Song 2", "Compilation", null, null);

		var result = LocalIdentifier.identify(List.of(t1, t2));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).albumArtist()).isEqualTo("Various Artists");
		assertThat(result.albums().get(0).tracks()).hasSize(2);
	}

	@Test
	void theSameAlbumTagOfDifferentArtistsInDifferentFoldersStaysOnSeparateAlbumsAndDoesNotBecomeVariousArtists() {
		// Title collision: two distinct "Greatest Hits", by two different artists, in two different
		// folders. Merging by title alone would fuse them into a single false Various Artists that Tier
		// 2 could no longer fix (the album tag is already present and real).
		var queen1 = track("/music/Queen", "01.mp3", "Queen", "Bohemian Rhapsody", "Greatest Hits", null, null);
		var queen2 = track("/music/Queen", "02.mp3", "Queen", "Somebody To Love", "Greatest Hits", null, null);
		var bonJovi1 = track("/music/Bon Jovi", "01.mp3", "Bon Jovi", "Livin' On A Prayer", "Greatest Hits", null, null);
		var bonJovi2 = track("/music/Bon Jovi", "02.mp3", "Bon Jovi", "It's My Life", "Greatest Hits", null, null);

		var result = LocalIdentifier.identify(List.of(queen1, queen2, bonJovi1, bonJovi2));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(2);
		assertThat(result.albums())
		                           .extracting(Album::albumArtist)
		                           .containsExactlyInAnyOrder("Queen", "Bon Jovi");
		for (Album album : result.albums()) {
			assertThat(album.title()).isEqualTo("Greatest Hits");
			assertThat(album.tracks()).hasSize(2);
		}
	}

	@Test
	void aTrackWithARealAlbumTagButNoOtherMatchingTracksStillBecomesAnAlbum() {
		var single = track("/music/Rare", "01.mp3", "Artist", "Title", "Little Known Album", "Artist", null);

		var result = LocalIdentifier.identify(List.of(single));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).completeness()).isEqualTo(AlbumCompleteness.UNVERIFIED);
	}

	@Test
	void anItalianLocalizedPlaceholderIsTreatedAsMissingAndDoesNotMergeDifferentAlbums() {
		// Two DIFFERENT Pink Floyd albums, both tagged "Album sconosciuto" by a localized rip (e.g.
		// Windows Media Player in Italian). An English-only placeholder list would fuse them into a
		// single false album of 4 tracks.
		var t1 = track("/music/Floyd A", "01.mp3", "Pink Floyd", "Title 1", "Album sconosciuto", "Pink Floyd", null);
		var t2 = track("/music/Floyd A", "02.mp3", "Pink Floyd", "Title 2", "Album sconosciuto", "Pink Floyd", null);
		var t3 = track("/music/Floyd B", "01.mp3", "Pink Floyd", "Title 3", "Album sconosciuto", "Pink Floyd", null);
		var t4 = track("/music/Floyd B", "02.mp3", "Pink Floyd", "Title 4", "Album sconosciuto", "Pink Floyd", null);

		var result = LocalIdentifier.identify(List.of(t1, t2, t3, t4));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(2);
		assertThat(result.albums()).extracting(Album::title).containsExactlyInAnyOrder("Floyd A", "Floyd B");
		for (Album album : result.albums()) {
			assertThat(album.tracks()).hasSize(2);
		}
	}

	@Test
	void aConsistentAlbumArtistAlwaysBeatsTheVariousArtistsRuleWhenTrackArtistsAreFeaturings() {
		// A consistent album artist tag ("Jay-Z") on all the tracks, but different track artists
		// because of the featurings: very normal in hip-hop and pop, not an edge case. The consistent
		// tag is a more direct signal than the variety of the track artists and must not be discarded
		// in favor of Various Artists: a mistake here is permanent, because an album with a "real" tag
		// never reopens the Tier 2 gate (SPEC.md 4.4).
		var t1 = track("/music/The Blueprint", "01.mp3", "Jay-Z", "Izzo", "The Blueprint", "Jay-Z", 2001);
		var t2 = track("/music/The Blueprint", "02.mp3", "Jay-Z feat. Eminem", "Renegade", "The Blueprint", "Jay-Z", 2001);
		var t3 = track("/music/The Blueprint", "03.mp3", "Jay-Z feat. Kanye West", "Bounce", "The Blueprint", "Jay-Z", 2001);

		var result = LocalIdentifier.identify(List.of(t1, t2, t3));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).albumArtist()).isEqualTo("Jay-Z");
	}

	@Test
	void aFolderWithTaggedAndUntaggedTracksInheritsTheDominantTagInsteadOfFragmenting() {
		var tagged1 = track("/music/Half curated", "01.mp3", "Artist", "Title 1", "Real Album", "Artist", 2005);
		var tagged2 = track("/music/Half curated", "02.mp3", "Artist", "Title 2", "Real Album", "Artist", 2005);
		var untagged1 = track("/music/Half curated", "03.mp3", "Artist", "Title 3", null, null, null);
		var untagged2 = track("/music/Half curated", "04.mp3", "Artist", "Title 4", null, null, null);

		var result = LocalIdentifier.identify(List.of(tagged1, tagged2, untagged1, untagged2));

		assertThat(result.isolatedTracks()).isEmpty();
		assertThat(result.albums()).hasSize(1);
		Album album = result.albums().get(0);
		assertThat(album.title()).isEqualTo("Real Album");
		assertThat(album.tracks()).containsExactlyInAnyOrder(tagged1, tagged2, untagged1, untagged2);
	}

	@Test
	void aFolderWithSeveralDifferentRealTagsDoesNotGuessWhichTheUntaggedTracksInherit() {
		var tagX1 = track("/music/Mixed", "01.mp3", "Artist", "Title 1", "Album X", "Artist", null);
		var tagY1 = track("/music/Mixed", "02.mp3", "Artist", "Title 2", "Album Y", "Artist", null);
		var untagged = track("/music/Mixed", "03.mp3", "Artist", "Title 3", null, null, null);

		var result = LocalIdentifier.identify(List.of(tagX1, tagY1, untagged));

		assertThat(result.albums()).extracting(Album::title).containsExactlyInAnyOrder("Album X", "Album Y");
		assertThat(result.isolatedTracks()).containsExactly(untagged);
	}

	@Test
	void threeWayCollisionAnOrphanWithoutArtistDoesNotClingToARealIdentityWhenTwoMatchTheTitle() {
		// Same title, two folders with different real artists, a third without any artist information:
		// neither real identity is unique for that title, so the orphan must not cling to either of them
		// (and the result must not depend on the iteration order of the input: checked by trying the
		// reverse order).
		var a1 = track("/music/A", "01.mp3", "Artist A", "Title 1", "X", "Artist A", null);
		var a2 = track("/music/A", "02.mp3", "Artist A", "Title 2", "X", "Artist A", null);
		var b1 = track("/music/B", "01.mp3", "Artist B", "Title 1", "X", "Artist B", null);
		var b2 = track("/music/B", "02.mp3", "Artist B", "Title 2", "X", "Artist B", null);
		var c1 = track("/music/C", "01.mp3", null, "Title 1", "X", null, null);
		var c2 = track("/music/C", "02.mp3", null, "Title 2", "X", null, null);

		var resultOrder1 = LocalIdentifier.identify(List.of(c1, c2, a1, a2, b1, b2));
		var resultOrder2 = LocalIdentifier.identify(List.of(a1, a2, b1, b2, c1, c2));

		for (var result : List.of(resultOrder1, resultOrder2)) {
			assertThat(result.isolatedTracks()).isEmpty();
			assertThat(result.albums()).hasSize(3);
			assertThat(result.albums())
			                           .extracting(Album::albumArtist)
			                           .containsExactlyInAnyOrder("Artist A", "Artist B", null);
		}
	}

	// Root of ~/Musica on the real run: 9 Sonata Arctica tracks without an album tag and 5 by Elio e le
	// Storie Tese with the tag "L'album Biango", all scattered in the root.
	@Test
	void inTheRootUntaggedTracksStayIsolatedAndTaggedOnesFormTheirAlbum() {
		Track sonata1 = track("/music", "02 - Weballergy.mp3", "Sonata Arctica", null, null, "Sonata Arctica", 2001);
		Track sonata2 = track("/music", "05 - Black Sheep.mp3", "Sonata Arctica", null, null, "Sonata Arctica", 2001);
		Track elio1 = track("/music", "06. Enlarge.mp3", "Elio e le Storie Tese", "Enlarge", "L'album Biango", null, 2013);
		Track elio2 = track("/music", "10. amore.mp3", "Elio e le Storie Tese", "Amore", "L'album Biango", null, 2013);

		var result = LocalIdentifier.identify(List.of(sonata1, sonata2, elio1, elio2), Path.of("/music"));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).title()).isEqualTo("L'album Biango");
		assertThat(result.albums().get(0).tracks()).containsExactlyInAnyOrder(elio1, elio2);
		assertThat(result.isolatedTracks()).containsExactlyInAnyOrder(sonata1, sonata2);
	}

	@Test
	void theSingleAlbumTagOfTheFolderIsNotInheritedAcrossDifferentArtists() {
		Track tagged = track("/music/mixed", "01.mp3", "Elio e le Storie Tese", "One", "L'album Biango", null, null);
		Track sameArtist = track("/music/mixed", "02.mp3", "Elio e le Storie Tese", "Two", null, null, null);
		Track otherArtist = track("/music/mixed", "03.mp3", "Sonata Arctica", "Three", null, null, null);
		Track otherArtist2 = track("/music/mixed", "04.mp3", "Sonata Arctica", "Four", null, null, null);

		var result = LocalIdentifier.identify(List.of(tagged, sameArtist, otherArtist, otherArtist2), Path.of("/music"));

		Album biango = result.albums()
		                     .stream()
		                     .filter(a -> a.title().equals("L'album Biango"))
		                     .findFirst()
		                     .orElseThrow();
		assertThat(biango.tracks()).containsExactlyInAnyOrder(tagged, sameArtist);
		assertThat(biango.tracks()).doesNotContain(otherArtist, otherArtist2);
	}

	// Linkin Park on the real run: a single .wma with album_artist "Artista sconosciuto" became the
	// artist of the whole group.
	@Test
	void aPlaceholderAlbumArtistDoesNotBecomeTheArtistOfTheGroup() {
		Track a = track("/music/Linkin Park/Hybrid Theory", "01.mp3", "Linkin Park", "Paper Cut", null, null, null);
		Track b = track("/music/Linkin Park/Hybrid Theory", "06.wma", "Linkin Park", "What I've Done", null, "Artista sconosciuto", null);

		var result = LocalIdentifier.identify(List.of(a, b), Path.of("/music"));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).albumArtist()).isEqualTo("Linkin Park");
	}

	// "Red Hot Chili Peppers/" on the real run: singles from different records without an album tag,
	// in a folder named after the artist. It is not an album: the tracks stay isolated for Tier 2.
	@Test
	void aFolderNamedAfterTheArtistWithoutAlbumTagDoesNotBecomeAnAlbum() {
		Track a = track("/music/Red Hot Chili Peppers", "05.mp3", "Red Hot Chili Peppers", "Under the Bridge", null, null, null);
		Track b = track("/music/Red Hot Chili Peppers", "07.mp3", "Red Hot Chili Peppers", "Otherside", null, null, null);

		var result = LocalIdentifier.identify(List.of(a, b), Path.of("/music"));

		assertThat(result.albums()).isEmpty();
		assertThat(result.isolatedTracks()).containsExactlyInAnyOrder(a, b);
	}

	// Remedy Lane on the real run: the same album label in a standard folder (here 2 tracks) and in a
	// "(Japanese Edition)" one with one more song.
	@Test
	void aFolderWithAnEditionQualifierAndExtraSongsIsASeparateAlbum() {
		String std = "/music/Pain Of Salvation/2002 - Remedy Lane";
		String jp = "/music/Pain Of Salvation/2002 - Remedy Lane (Japanese Edition)";
		Track s1 = track(std, "01.mp3", "Pain Of Salvation", "Of Two Beginnings", "Remedy Lane", null, 2002);
		Track s2 = track(std, "02.mp3", "Pain Of Salvation", "Ending Theme", "Remedy Lane", null, 2002);
		Track j1 = track(jp, "01.mp3", "Pain Of Salvation", "Of Two Beginnings", "Remedy Lane", null, 2002);
		Track j2 = track(jp, "02.mp3", "Pain Of Salvation", "Ending Theme", "Remedy Lane", null, 2002);
		Track bonus = track(jp, "14.mp3", "Pain Of Salvation", "Thorn Clown", "Remedy Lane", null, 2002);

		var result = LocalIdentifier.identify(List.of(s1, s2, j1, j2, bonus), Path.of("/music"));

		assertThat(result.albums()).extracting(Album::title)
		                           .containsExactlyInAnyOrder("Remedy Lane", "Remedy Lane (Japanese Edition)");
	}

	@Test
	void aFolderWithAQualifierButTheSameTracklistStaysTheSameAlbum() {
		Track a = track("/music/Deep Purple/Deep Purple - 2013 - Now What", "01.mp3", "Deep Purple", "A Simple Song", "Now What", null, 2013);
		Track b = track("/music/Deep Purple/Now What (Limited Edition)", "01.mp3", "Deep Purple", "A Simple Song", "Now What", null, 2013);

		var result = LocalIdentifier.identify(List.of(a, b), Path.of("/music"));

		assertThat(result.albums()).hasSize(1);
		assertThat(result.albums().get(0).tracks()).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void aFolderNamedAfterAnArtistPresentInEveryCreditDoesNotBecomeAnAlbum() {
		Track a = track("/music/Dream Theater", "a.mp3", "Dream Theater", "Pull Me Under", null, null, null);
		Track b = track("/music/Dream Theater", "b.mp3", "Pink Floyd, Kansas, Queen & Dream Theater", "The Big Medley", null, null, null);

		var result = LocalIdentifier.identify(List.of(a, b), Path.of("/music"));

		assertThat(result.albums()).isEmpty();
	}
}
