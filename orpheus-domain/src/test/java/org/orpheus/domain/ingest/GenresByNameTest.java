package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.ingest.GenresByName.Names;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseGroupSearchPort.FoundReleaseGroup;

class GenresByNameTest {

	private static Track t(@Nullable String artist, @Nullable String album, @Nullable String albumArtist) {
		Path p = Path.of("/music/Deep Purple/Burn/01.mp3");
		TrackMetadata m = new TrackMetadata(artist, "Title", album, albumArtist, 1974, 1, null, ExternalIds.UNKNOWN);
		return new Track(p, m, new Provenance(p, ProvenanceState.INTACT));
	}

	private static Album album(Track... tracks) {
		return new Album("Burn", null, 1974, AlbumCompleteness.UNVERIFIED, List.of(tracks));
	}

	private static FoundReleaseGroup rg(String id, String title, String credit, String artistId, String type, String... secondary) {
		return new FoundReleaseGroup(id, title, credit, List.of(artistId), type, List.of(secondary));
	}

	@Test
	void aConsistentlyTaggedFolderGivesArtistAndTitle() {
		assertThat(GenresByName.orderedAlbumNames(album(t("Deep Purple", "Burn", null), t("Deep Purple", "Burn", null)))).contains(new Names("Deep Purple", "Burn"));
		assertThat(GenresByName.orderedAlbumNames(album(t("Ian Gillan", "Burn", "Deep Purple"), t("Glenn Hughes", "Burn", "Deep Purple")))).contains(new Names("Deep Purple", "Burn"));
	}

	@Test
	void articleAmpersandAndPunctuationDoNotCount() {
		assertThat(GenresByName.normalize("Darkness&Hope")).isEqualTo(GenresByName.normalize("Darkness and Hope"));
		assertThat(GenresByName.normalize("Forgotten Tales")).isEqualTo(GenresByName.normalize("The Forgotten Tales"));
		assertThat(GenresByName.normalize("Where's The Beef-")).isEqualTo(GenresByName.normalize("Where\u2019s the Beef?"));
		assertThat(GenresByName.normalize("Burn")).isNotEqualTo(GenresByName.normalize("Burn / Stormbringer"));
	}

	// The year of the first release of the chosen release group, for albums without a year in the tags.
	@Test
	void theChoiceCarriesTheYearOfTheReleaseGroup() {
		var candidates = List.of(new FoundReleaseGroup("album", "Burn", "Deep Purple", List.of("dp"), "Album", List.of(), 1974), new FoundReleaseGroup("ep", "Burn", "Deep Purple", List.of("dp"), "EP", List.of(), 1975));

		assertThat(GenresByName.choose(new Names("Deep Purple", "Burn"), candidates).orElseThrow().year())
		                                                                                                  .contains(1974);
	}

	@Test
	void aFolderWithInconsistentOrMissingTagsIsNotSearched() {
		assertThat(GenresByName.orderedAlbumNames(album(t("Deep Purple", "Burn", null), t("Deep Purple", null, null)))).isEmpty();
		assertThat(GenresByName.orderedAlbumNames(album(t("Deep Purple", "Burn", null), t("Rainbow", "Burn", null)))).isEmpty();
		assertThat(GenresByName.orderedAlbumNames(album(t("A", "Hits", "Various Artists"), t("B", "Hits", "Various Artists")))).isEmpty();
	}

	// "Burn": two EPs and an album with the same name by the same artist, the album counts.
	@Test
	void amongSeveralMatchesTheSingleAlbumCounts() {
		var candidates = List.of(rg("ep1", "Burn", "Deep Purple", "dp", "EP"), rg("ep2", "Burn", "Deep Purple", "dp", "EP"), rg("album", "Burn", "Deep Purple", "dp", "Album"), rg("comp", "Burn / Stormbringer", "Deep Purple", "dp", "Album", "Compilation"));

		var choice = GenresByName.choose(new Names("Deep Purple", "Burn"), candidates).orElseThrow();

		assertThat(choice.releaseGroupId()).contains("album");
		assertThat(choice.artistId()).contains("dp");
	}

	@Test
	void severalSameNamedAlbumsOfTheSameArtistGiveTheArtistGenres() {
		var candidates = List.of(rg("w94", "Weezer", "Weezer", "wz", "Album"), rg("w01", "Weezer", "Weezer", "wz", "Album"));

		var choice = GenresByName.choose(new Names("Weezer", "Weezer"), candidates).orElseThrow();

		assertThat(choice.releaseGroupId()).isEmpty();
		assertThat(choice.artistId()).contains("wz");
	}

	@Test
	void withoutAnExactMatchNoChoice() {
		assertThat(GenresByName.choose(new Names("ACDC", "Back in Black"), List.of(rg("x", "Back in Black", "AC/DC", "acdc", "Album")))).isEmpty();
		assertThat(GenresByName.choose(new Names("Artist", "Greatest Hits"), List.of(rg("a", "Greatest Hits", "Artist", "id1", "Album", "Compilation"), rg("b", "Greatest Hits", "Artist", "id2", "Album", "Compilation")))).isEmpty();
	}

	@Test
	void capitalizationSpacesAndTypographicCharactersDoNotCount() {
		var choice = GenresByName.choose(new Names("Guns N' Roses", "Appetite  for destruction"), List.of(rg("x", "Appetite for Destruction", "Guns N’ Roses", "gnr", "Album")));

		assertThat(choice).map(GenresByName.Choice::releaseGroupId).contains(Optional.of("x"));
		assertThat(choice).map(GenresByName.Choice::artistCredit).contains("Guns N\u2019 Roses");
		assertThat(choice).map(GenresByName.Choice::title).contains("Appetite for Destruction");
	}

	@Test
	void theMusicBrainzTitleReplacesOnlyADifferentSpelling() {
		var choice = new GenresByName.Choice(Optional.of("rg"), Optional.of("pos"), "Pain of Salvation", "Entropia");

		assertThat(GenresByName.albumTitle("ENTROPIA", choice)).isEqualTo("Entropia");
		assertThat(GenresByName.albumTitle("Entropia (Japanese Edition)", choice)).isEqualTo("Entropia (Japanese Edition)");
	}
}
