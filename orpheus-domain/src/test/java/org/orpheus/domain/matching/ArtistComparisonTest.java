package org.orpheus.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// Real variants found comparing the folder names of ~/Musica with the MusicBrainz credits (run of
// 2026-09-26, ADR-0018).
class ArtistComparisonTest {

	@Test
	void punctuationAndUnicodeDashesDoNotCount() {
		assertThat(ArtistComparison.matches("ACDC", List.of("AC/DC"))).isTrue();
		assertThat(ArtistComparison.matches("Guns N Roses", List.of("Guns N' Roses"))).isTrue();
		assertThat(ArtistComparison.matches("Blink 182", List.of("blink‐182"))).isTrue();
		assertThat(ArtistComparison.matches("Greenday", List.of("Green Day"))).isTrue();
	}

	@Test
	void articleTheAtTheStartOrAtTheEnd() {
		assertThat(ArtistComparison.matches("Calling, The", List.of("The Calling"))).isTrue();
		assertThat(ArtistComparison.matches("Cranberries, The", List.of("The Cranberries"))).isTrue();
		assertThat(ArtistComparison.matches("Offspring", List.of("The Offspring"))).isTrue();
	}

	@Test
	void ampersandEqualsAnd() {
		assertThat(ArtistComparison.matches("Joan Jett & the Blackhearts", List.of("Joan Jett and the Blackhearts"))).isTrue();
	}

	@Test
	void aSmallTypoPassesButADifferentNameDoesNot() {
		assertThat(ArtistComparison.matches("Cramberries, The", List.of("The Cranberries"))).isTrue();
		assertThat(ArtistComparison.matches("Bon Jovi", List.of("Andy Williams"))).isFalse();
		assertThat(ArtistComparison.matches("Foo Fighters", List.of("Carl Douglas"))).isFalse();
	}

	@Test
	void aMultipleCreditIsRecognizedBySingleArtistOrWhole() {
		assertThat(ArtistComparison.matches("Queen", List.of("Queen", "David Bowie"))).isTrue();
		assertThat(ArtistComparison.matches("Queen David Bowie", List.of("Queen", "David Bowie"))).isTrue();
		assertThat(ArtistComparison.matches("Foo Fighters", List.of("Queen", "David Bowie"))).isFalse();
	}

	@Test
	void aBlankArtistOrAnEmptyCreditNeverMatch() {
		assertThat(ArtistComparison.matches("", List.of("Queen"))).isFalse();
		assertThat(ArtistComparison.matches("Queen", List.of())).isFalse();
	}
}
