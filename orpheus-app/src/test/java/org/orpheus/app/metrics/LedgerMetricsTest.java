package org.orpheus.app.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.orpheus.app.metrics.LedgerMetrics.Entry;

// Cases taken from the real run of 2026-09-26 (docs/TODO.md, "Damage observed").
class LedgerMetricsTest {

	private static final Path SRC = Path.of("/src");
	private static final Path DST = Path.of("/dst");

	private static Entry entry(String source, String destination, String reason) {
		return new Entry(SRC.resolve(source), DST.resolve(destination), "IMPORTED_AS_IS", reason);
	}

	private static LedgerMetrics.Result compute(Map<String, String> tagArtists, Entry... entries) {
		return LedgerMetrics.compute(List.of(entries), SRC, DST, p -> tagArtists.get(SRC.relativize(p).toString()));
	}

	@Test
	void anArtistFolderPromotedToAlbumIsAFakeAlbum() {
		var r = compute(
		        Map.of("Linkin Park/ - Numb.mp3", "Linkin Park", "Deep Purple/Burn/01 - Burn.mp3", "Deep Purple"), entry("Linkin Park/ - Numb.mp3", "Unknown Artist/2003 - Linkin Park/Numb.mp3", "Tier 0/1"), entry("Red Hot Chili Peppers/05.mp3", "Red Hot Chili Peppers/1991 - Red Hot Chili Peppers/05.mp3", "Tier 2"), entry("Deep Purple/Burn/01 - Burn.mp3", "Deep Purple/1974 - Burn/01 - Burn.mp3", "Tier 0/1"));

		assertThat(r.fakeAlbums()).isEqualTo(2);
	}

	@Test
	void aRealSameNamedAlbumInItsFolderIsNotAFakeAlbum() {
		var r = compute(Map.of("Black Sabbath/Black Sabbath/01.mp3", "Black Sabbath"), entry("Black Sabbath/Black Sabbath/01.mp3", "Black Sabbath/1970 - Black Sabbath/01.mp3", ""));

		assertThat(r.fakeAlbums()).isZero();
	}

	@Test
	void numericSuffixesAndUnknownTracks() {
		var r = compute(
		        Map.of(), entry("a/1.mp3", "A/2000 - X/01 - Burn (2).mp3", ""), entry("a/2.mp3", "A/2000 - X/02 - Unknown Track.mp3", ""), entry("a/3.flac", "A/2000 - X/Unknown Track (3).flac", ""));

		assertThat(r.numericSuffixes()).isEqualTo(2);
		assertThat(r.unknownTracks()).isEqualTo(2);
	}

	@Test
	void anUnidentifiedTrackWithAReleaseCountsOnlyIfTier2FoundIt() {
		var r = compute(
		        Map.of(), entry("x/a.mp3", "Bon Jovi/Unidentified/a.mp3", "Tier 2: individual identification on release abc-1"), entry("x/b.mp3", "Bon Jovi/Unidentified/b.mp3", "Isolated track"));

		assertThat(r.unidentifiedWithRelease()).isEqualTo(1);
	}

	@Test
	void aYearInTheFolderNameDifferentFromTheDestinationOne() {
		var r = compute(
		        Map.of(), entry("Deep Purple/Deep Purple - 1971 - Fireball/01.mp3", "Deep Purple/Unknown Year - Fireball/01.mp3", ""), entry("Deep Purple/Deep Purple - 1987 - The House Of Blue Light/01.mp3", "Deep Purple/1982 - The House Of Blue Light/01.mp3", ""), entry("Deep Purple/Deep Purple - 1974 - Burn/01.mp3", "Deep Purple/1974 - Burn/01.mp3", ""));

		assertThat(r.conflictingYears()).isEqualTo(2);
	}

	@Test
	void differentArtistsInTheSameAlbumButNotInVariousArtists() {
		var r = compute(
		        Map.of("03.mp3", "Sonata Arctica", "06.mp3", "Elio e le Storie Tese", "c1.mp3", "Queen", "c2.mp3", "Blur"), entry("03.mp3", "Sonata Arctica/2001 - L'album Biango/03.mp3", ""), entry("06.mp3", "Sonata Arctica/2001 - L'album Biango/06.mp3", ""), entry("c1.mp3", "Various Artists/1997 - Compilation/01.mp3", ""), entry("c2.mp3", "Various Artists/1997 - Compilation/02.mp3", ""));

		assertThat(r.tracksInAlbumsWithDifferentArtists()).isEqualTo(2);
	}

	@Test
	void aFeaturingDoesNotMakeAnAlbumMixed() {
		var r = compute(Map.of("q1.mp3", "Queen", "q2.mp3", "Queen & David Bowie"), entry("q1.mp3", "Queen/1982 - Hot Space/01.mp3", ""), entry("q2.mp3", "Queen/1982 - Hot Space/11.mp3", ""));

		assertThat(r.tracksInAlbumsWithDifferentArtists()).isZero();
	}

	@Test
	void releasesByUnrelatedArtistsExcludingDeclaredContrasts() {
		var r = compute(
		        Map.of("Bon Jovi/a.mp3", "Bon Jovi", "Bon Jovi/b.mp3", "Bon Jovi", "Beatles/c.mp3", "Beatles"), entry("Bon Jovi/a.mp3", "Bon Jovi/1963 - Bon Jovi/a.mp3", "Tier 2: individual identification on release r1 by \"Andy Williams\""), entry("Bon Jovi/b.mp3", "Bon Jovi/1992 - Keep the Faith/b.mp3", "Tier 2: individual identification on release r2 by \"Bon Jovi\""), entry("Beatles/c.mp3", "Beatles/1969 - Beatles/c.mp3", "Tier 2: individual identification on release r3 by \"The Archies\" - fingerprint contrasting the tag artist \"Beatles\""));

		assertThat(r.releasesByUnrelatedArtists()).isEqualTo(1);
	}
}
