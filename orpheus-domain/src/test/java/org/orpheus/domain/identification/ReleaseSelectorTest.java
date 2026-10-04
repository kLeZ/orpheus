package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReleaseSelectorTest {

	@Test
	void anAlbumBeatsALeadSingleEvenWithAnOlderDate() {
		Release leadSingle = new Release("single", ReleaseType.SINGLE, ReleaseDate.exact(1974, 1, 1));
		Release album = new Release("album", ReleaseType.ALBUM, ReleaseDate.yearOnly(1975));

		Optional<Release> winner = ReleaseSelector.choose(List.of(leadSingle, album));

		assertThat(winner).contains(album);
	}

	@Test
	void aNewerAndMorePreciseRemasterDoesNotBeatTheOriginal() {
		Release original = new Release("original", ReleaseType.ALBUM, ReleaseDate.yearOnly(1975));
		Release remaster = new Release("remaster", ReleaseType.ALBUM, ReleaseDate.exact(2015, 3, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(remaster, original));

		assertThat(winner).contains(original);
	}

	@Test
	void aYearOnlyDateOfTheSameYearDoesNotWinAgainstAPreciseDate() {
		Release yearOnly = new Release("year-only", ReleaseType.ALBUM, ReleaseDate.yearOnly(1975));
		Release precise = new Release("precise", ReleaseType.ALBUM, ReleaseDate.exact(1975, 6, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(yearOnly, precise));

		assertThat(winner).contains(precise);
	}

	@Test
	void aYearOnlyDateOfAnEarlierYearRightlyWinsAgainstALaterPreciseDate() {
		Release earlierYearOnly = new Release("year-only", ReleaseType.ALBUM, ReleaseDate.yearOnly(1974));
		Release laterPrecise = new Release("precise", ReleaseType.ALBUM, ReleaseDate.exact(1975, 1, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(laterPrecise, earlierYearOnly));

		assertThat(winner).contains(earlierYearOnly);
	}

	@Test
	void withoutAlbumsAmongTheCandidatesRule2AppliesToAll() {
		Release ep = new Release("ep", ReleaseType.EP, ReleaseDate.exact(1980, 1, 1));
		Release single = new Release("single", ReleaseType.SINGLE, ReleaseDate.exact(1979, 6, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(ep, single));

		assertThat(winner).contains(single);
	}

	@Test
	void otherBehavesAsANonAlbumAndIsDiscardedWhenThereIsAnAlbum() {
		Release live = new Release("live", ReleaseType.OTHER, ReleaseDate.exact(1970, 1, 1));
		Release album = new Release("album", ReleaseType.ALBUM, ReleaseDate.exact(1980, 1, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(live, album));

		assertThat(winner).contains(album);
	}

	@Test
	void noCandidateReturnsAnEmptyOptional() {
		Optional<Release> winner = ReleaseSelector.choose(List.of());

		assertThat(winner).isEmpty();
	}

	@Test
	void nullCandidatesNotAllowed() {
		assertThatThrownBy(() -> ReleaseSelector.choose(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void aSingleCandidateAlwaysWins() {
		Release only = new Release("only", ReleaseType.SINGLE, ReleaseDate.yearOnly(2000));

		Optional<Release> winner = ReleaseSelector.choose(List.of(only));

		assertThat(winner).contains(only);
	}

	@Test
	void withSeveralAlbumsAmongTheCandidatesRule2AppliesOnlyAmongTheAlbums() {
		Release olderAlbum = new Release("album-1973", ReleaseType.ALBUM, ReleaseDate.yearOnly(1973));
		Release newerAlbum = new Release("album-1980", ReleaseType.ALBUM, ReleaseDate.exact(1980, 1, 1));
		Release single = new Release("single", ReleaseType.SINGLE, ReleaseDate.exact(1970, 1, 1));

		Optional<Release> winner = ReleaseSelector.choose(List.of(newerAlbum, single, olderAlbum));

		assertThat(winner).contains(olderAlbum);
	}

	@Test
	void twoAlbumsWithTheSameExactDateTheFirstOfTheListWins() {
		// SPEC.md/ADR-001 specify no tie-break for two genuinely different Album releases (e.g. US and
		// UK releases of the same day) with the same exact date: any choice conforms, so the
		// deterministic "first of the list" choice is legitimate, not a defect. Documented here to make
		// explicit that it is an arbitrary but conforming choice, neither random behavior nor a missing
		// weighted tie-break.
		Release us = new Release("us", ReleaseType.ALBUM, ReleaseDate.exact(1980, 1, 1));
		Release uk = new Release("uk", ReleaseType.ALBUM, ReleaseDate.exact(1980, 1, 1));

		assertThat(ReleaseSelector.choose(List.of(us, uk))).contains(us);
		assertThat(ReleaseSelector.choose(List.of(uk, us))).contains(uk);
	}
}
