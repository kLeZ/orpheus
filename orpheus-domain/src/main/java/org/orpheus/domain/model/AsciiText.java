package org.orpheus.domain.model;

import java.util.regex.Pattern;

/**
 * Typographic characters replaced by their ASCII equivalent, in folder names and in tags (ADR-0021,
 * user decision of 2026-09-27): full-text search does not match "blink‐182" (U+2010, the MusicBrainz
 * form) with "blink-182". Accented letters stay: they are spelling, not typography.
 */
public final class AsciiText {

	private static final Pattern DASHES = Pattern.compile("[\\u2010-\\u2015\\u2212\\uFE58\\uFE63\\uFF0D]");
	private static final Pattern APOSTROPHES = Pattern.compile("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u02BC\\u00B4]");
	private static final Pattern QUOTES = Pattern.compile("[\\u201C\\u201D\\u201E\\u201F\\u2033\\u00AB\\u00BB]");
	private static final Pattern SPACES = Pattern.compile("[\\u00A0\\u2000-\\u200A\\u202F\\u205F]");
	private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");

	private AsciiText() {
	}

	public static String fromTypographic(String text) {
		String s = DASHES.matcher(text).replaceAll("-");
		s = APOSTROPHES.matcher(s).replaceAll("'");
		s = QUOTES.matcher(s).replaceAll("\"");
		s = SPACES.matcher(s).replaceAll(" ");
		s = INVISIBLE.matcher(s).replaceAll("");
		return s.replace("…", "...");
	}
}
