package org.orpheus.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class NormalizedLevenshteinTest {

	@Test
	void bothEmptyIsZero() {
		assertThat(NormalizedLevenshtein.distance("", "")).isEqualTo(0.0);
	}

	@Test
	void whitespaceOnlyIsTreatedAsEmpty() {
		assertThat(NormalizedLevenshtein.distance("   ", "")).isEqualTo(0.0);
	}

	@Test
	void oneEmptySaturatesToMaximum() {
		assertThat(NormalizedLevenshtein.distance("Beatles", "")).isEqualTo(1.0);
		assertThat(NormalizedLevenshtein.distance("", "Beatles")).isEqualTo(1.0);
	}

	@Test
	void identicalStringsAreZero() {
		assertThat(NormalizedLevenshtein.distance("Radiohead", "Radiohead")).isEqualTo(0.0);
	}

	@Test
	void singleDifferingCharacterIsMaximum() {
		assertThat(NormalizedLevenshtein.distance("a", "b")).isEqualTo(1.0);
	}

	@Test
	void isCaseInsensitive() {
		assertThat(NormalizedLevenshtein.distance("ABBA", "abba")).isEqualTo(0.0);
	}

	@ParameterizedTest
	@MethodSource("diacriticPairs")
	void diacriticsAreFoldedToBaseLetter(String withDiacritics, String withoutDiacritics) {
		assertThat(NormalizedLevenshtein.distance(withDiacritics, withoutDiacritics)).isEqualTo(0.0);
	}

	static Stream<Arguments> diacriticPairs() {
		return Stream.of(
		        Arguments.of("café", "cafe"), Arguments.of("München", "Munchen"), Arguments.of("Sigur Rós", "Sigur Ros"), Arguments.of("Beyoncé", "Beyonce"));
	}

	@ParameterizedTest
	@MethodSource("extraLatinLetterPairs")
	void extraLatinLettersWithoutCanonicalDecompositionAreFolded(String withExtraLetter, String withBaseLetter) {
		assertThat(NormalizedLevenshtein.distance(withExtraLetter, withBaseLetter)).isEqualTo(0.0);
	}

	static Stream<Arguments> extraLatinLetterPairs() {
		return Stream.of(
		        Arguments.of("Røyksopp", "Royksopp"), Arguments.of("Þórr", "Thorr"), Arguments.of("Guðmundsdóttir", "Gudmundsdottir"), Arguments.of("Björk", "Bjork"));
	}

	@Test
	void punctuationAndWhitespaceAreIgnoredTogether() {
		assertThat(NormalizedLevenshtein.distance("The Beatles", "TheBeatles")).isEqualTo(0.0);
		assertThat(NormalizedLevenshtein.distance("AC/DC", "ACDC")).isEqualTo(0.0);
		assertThat(NormalizedLevenshtein.distance("Guns N' Roses", "Guns N Roses")).isEqualTo(0.0);
	}

	@Test
	void veryDifferentLengthsAreProportional() {
		double distance = NormalizedLevenshtein.distance("a", "abcdefghij");
		assertThat(distance).isEqualTo(9.0 / 10.0);
	}

	@Test
	void isSymmetric() {
		assertThat(NormalizedLevenshtein.distance("Nirvana", "Nirwana"))
		                                                                .isEqualTo(NormalizedLevenshtein.distance("Nirwana", "Nirvana"));
	}

	@ParameterizedTest
	@MethodSource("arbitraryPairs")
	void isAlwaysWithinUnitRange(String a, String b) {
		double distance = NormalizedLevenshtein.distance(a, b);
		assertThat(distance).isBetween(0.0, 1.0);
	}

	static Stream<Arguments> arbitraryPairs() {
		return Stream.of(
		        Arguments.of("Metallica", "Megadeth"), Arguments.of("", "Anything at all"), Arguments.of("同じ文字列", "同じ文字列"), Arguments.of("Björk", "Bjork Guðmundsdóttir"));
	}

	@Test
	void rejectsNullArguments() {
		assertThatThrownBy(() -> NormalizedLevenshtein.distance(null, "x")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> NormalizedLevenshtein.distance("x", null)).isInstanceOf(NullPointerException.class);
	}
}
