package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReleaseDateTest {

	@Test
	void differentYearsDecideOnTheirOwnWhateverThePrecision() {
		ReleaseDate earlierYearOnly = ReleaseDate.yearOnly(1974);
		ReleaseDate laterPrecise = ReleaseDate.exact(1975, 1, 1);

		assertThat(earlierYearOnly).isLessThan(laterPrecise);
		assertThat(laterPrecise).isGreaterThan(earlierYearOnly);
	}

	@Test
	void aPreciseDateWinsOverAYearOnlyDateOfTheSameYear() {
		ReleaseDate yearOnly = ReleaseDate.yearOnly(1975);
		ReleaseDate precise = ReleaseDate.exact(1975, 6, 1);

		assertThat(precise).isLessThan(yearOnly);
		assertThat(yearOnly).isGreaterThan(precise);
	}

	@Test
	void twoYearOnlyDatesOfTheSameYearAreEquivalent() {
		ReleaseDate a = ReleaseDate.yearOnly(1975);
		ReleaseDate b = ReleaseDate.yearOnly(1975);

		assertThat(a.compareTo(b)).isZero();
		assertThat(a).isEqualTo(b);
	}

	@Test
	void twoPreciseDatesOfTheSameYearCompareByMonthAndDay() {
		ReleaseDate earlier = ReleaseDate.exact(1975, 3, 10);
		ReleaseDate later = ReleaseDate.exact(1975, 3, 20);

		assertThat(earlier).isLessThan(later);
	}

	@Test
	void monthWithoutDayNotAllowed() {
		assertThatThrownBy(() -> new ReleaseDate(1975, 6, null))
		                                                        .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void dayWithoutMonthNotAllowed() {
		assertThatThrownBy(() -> new ReleaseDate(1975, null, 1))
		                                                        .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aCalendarInvalidDateIsNotAllowed() {
		assertThatThrownBy(() -> ReleaseDate.exact(1975, 2, 30))
		                                                        .isInstanceOf(java.time.DateTimeException.class);
	}

	@Test
	void preciseTellsTheTwoFormsApart() {
		assertThat(ReleaseDate.yearOnly(1975).precise()).isFalse();
		assertThat(ReleaseDate.exact(1975, 1, 1).precise()).isTrue();
	}
}
