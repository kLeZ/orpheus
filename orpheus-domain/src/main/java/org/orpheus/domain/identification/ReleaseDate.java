package org.orpheus.domain.identification;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A release date with variable precision: either the year alone, or a full year, month and day
 * (SPEC.md section 4.4, ADR-001). MusicBrainz publishes release dates in both forms, and treating
 * them as equivalent (for example forcing a year-only date to January 1st, or comparing only the
 * year) is exactly the bug already met in production (ADR-001): a year-only date does not carry the
 * information needed to say whether it comes before or after a precise date of the same year, so the
 * comparison has to handle this ambiguity explicitly instead of faking a precision the data does not
 * have.
 */
public record ReleaseDate(int year,
                          @Nullable Integer month,
                          @Nullable Integer day) implements Comparable<ReleaseDate> {

	public ReleaseDate {
		if ((month == null) != (day == null)) {
			throw new IllegalArgumentException("month and day must be both present or both absent");
		}
		if (month != null) {
			LocalDate.of(year, month, day);
		}
	}

	public static ReleaseDate yearOnly(int year) {
		return new ReleaseDate(year, null, null);
	}

	public static ReleaseDate exact(int year, int month, int day) {
		return new ReleaseDate(year, month, day);
	}

	public boolean precise() {
		return month != null;
	}

	/**
	 * Different years decide on their own, whatever the precision of both dates: a whole year always
	 * comes entirely before any day of the following year, so asking which one is "more precise" is
	 * unnecessary (and wrong) in that case. With the same year, a precise date always wins over a
	 * year-only date of that year: since it cannot be known whether the latter falls before or after,
	 * it is not granted the win, so as not to repeat the bug of ADR-001. Two identical year-only dates
	 * stay equivalent: there is no basis to tell them apart.
	 */
	@Override
	public int compareTo(ReleaseDate other) {
		Objects.requireNonNull(other, "other");
		if (year != other.year) {
			return Integer.compare(year, other.year);
		}
		if (precise() && other.precise()) {
			int byMonth = Integer.compare(month, other.month);
			if (byMonth != 0) {
				return byMonth;
			}
			return Integer.compare(day, other.day);
		}
		if (precise() == other.precise()) {
			return 0;
		}
		return precise() ? -1 : 1;
	}
}
