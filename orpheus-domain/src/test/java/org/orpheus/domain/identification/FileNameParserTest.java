package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Real file names from ~/Musica (run of 2026-09-26).
class FileNameParserTest {

	@Test
	void numberAndTitle() {
		var r = FileNameParser.parse("05 - Crawling.mp3", "Linkin Park");
		assertThat(r.number()).hasValue(5);
		assertThat(r.title()).hasValue("Crawling");
		assertThat(r.artist()).isEmpty();
	}

	@Test
	void numberWithDotAndTitleWithParentheses() {
		var r = FileNameParser.parse("06. Enlarge (Your penis).mp3", null);
		assertThat(r.number()).hasValue(6);
		assertThat(r.title()).hasValue("Enlarge (Your penis)");
	}

	@Test
	void leadingDashWithoutNumber() {
		var r = FileNameParser.parse(" - Numb.mp3", "Linkin Park");
		assertThat(r.number()).isEmpty();
		assertThat(r.title()).hasValue("Numb");
	}

	@Test
	void aKnownLeadingArtistIsRemoved() {
		var r = FileNameParser.parse("Ray Charles - Amazing Grace.mp3", "Ray Charles");
		assertThat(r.artist()).hasValue("Ray Charles");
		assertThat(r.title()).hasValue("Amazing Grace");
	}

	@Test
	void withADifferentKnownArtistEverythingIsTitle() {
		var r = FileNameParser.parse(" - Take a look around - Punk cover.mp3", "Lagwagon");
		assertThat(r.artist()).isEmpty();
		assertThat(r.title()).hasValue("Take a look around - Punk cover");
	}

	@Test
	void numberInTheMiddle() {
		var r = FileNameParser.parse("Myr - Days Of Convergence - 01 - Apprentice.flac", null);
		assertThat(r.number()).hasValue(1);
		assertThat(r.artist()).hasValue("Myr");
		assertThat(r.title()).hasValue("Apprentice");
	}

	@Test
	void titlesStartingWithANumberStayTitles() {
		assertThat(FileNameParser.parse("21 Guns.mp3", "Green Day").title()).hasValue("21 Guns");
		assertThat(FileNameParser.parse("1979.mp3", null).title()).hasValue("1979");
		assertThat(FileNameParser.parse("09 - 36.mp3", null).title()).hasValue("36");
		assertThat(FileNameParser.parse("02 What'd I Say.mp3", null).number()).hasValue(2);
	}
}
