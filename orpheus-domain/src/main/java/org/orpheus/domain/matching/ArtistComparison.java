package org.orpheus.domain.matching;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Comparison between an artist name as written in the tags or in a folder name and the artists
 * credited by MusicBrainz/AcoustID (ADR-0018). Case, diacritics, punctuation and spaces are already
 * neutralized by {@link NormalizedLevenshtein} ("ACDC" and "AC/DC", "Blink 182" and "blink‐182" at
 * distance zero); here only the variants that normalization does not cover are added: "&amp;" against
 * "and" and the article "The" at the start or moved to the end ("Calling, The").
 */
public final class ArtistComparison {

	// Threshold measured on the real run of 2026-09-26 (ADR-0018): 0.85 similarity, that is a
	// normalized distance of at most 0.15.
	static final double MAX_DISTANCE = 0.15;

	private static final Pattern TRAILING_ARTICLE = Pattern.compile("^(.*),\\s*the$");
	private static final Pattern LEADING_ARTICLE = Pattern.compile("^the\\s+");

	private ArtistComparison() {
	}

	/**
	 * True if {@code artist} is similar to at least one of the credited artists, taken one by one or
	 * joined: a credit like "Queen, David Bowie" must recognize both "Queen" and the whole credit
	 * written in a single tag.
	 */
	public static boolean matches(String artist, List<String> credits) {
		Objects.requireNonNull(artist, "artist");
		Objects.requireNonNull(credits, "credits");
		String a = canonical(artist);
		if (a.isBlank() || credits.isEmpty()) {
			return false;
		}
		for (String credit : credits) {
			if (credit != null && similar(a, canonical(credit))) {
				return true;
			}
		}
		return similar(a, canonical(String.join(" ", credits)));
	}

	private static boolean similar(String a, String b) {
		if (b.isBlank()) {
			return false;
		}
		return NormalizedLevenshtein.distance(a, b) <= MAX_DISTANCE;
	}

	private static String canonical(String s) {
		String lower = s.strip().toLowerCase(Locale.ROOT).replace("&", " and ");
		lower = TRAILING_ARTICLE.matcher(lower).replaceFirst("$1");
		return LEADING_ARTICLE.matcher(lower).replaceFirst("");
	}
}
