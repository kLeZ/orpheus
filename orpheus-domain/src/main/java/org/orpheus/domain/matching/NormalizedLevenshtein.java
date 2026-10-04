package org.orpheus.domain.matching;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class NormalizedLevenshtein {

	// Extended Latin letters without a canonical NFKD decomposition (they are not base letter plus
	// diacritic, they are letters of their own: bar or ligature), so the fold below has to be done
	// here explicitly before NFKD, not left to the Unicode normalization step.
	private static final Map<Integer, String> EXTRA_LATIN_FOLDS = Map.of(
	        (int) 'ø', "o", (int) 'þ', "th", (int) 'ð', "d", (int) 'ł', "l", (int) 'æ', "ae", (int) 'œ', "oe", (int) 'ß', "ss");

	private NormalizedLevenshtein() {
	}

	public static double distance(String a, String b) {
		Objects.requireNonNull(a, "a");
		Objects.requireNonNull(b, "b");

		int[] normalizedA = normalize(a);
		int[] normalizedB = normalize(b);

		int maxLength = Math.max(normalizedA.length, normalizedB.length);
		if (maxLength == 0) {
			return 0.0;
		}
		return rawDistance(normalizedA, normalizedB) / (double) maxLength;
	}

	// Punctuation and spaces are dropped together, not punctuation alone: it is the same criterion
	// as beets/distance.py (_string_dist_basic, regex [^a-z0-9]) this elementary block derives from
	// (ADR-006/ADR-015), and it keeps the maximum length denominator below consistent: "The Beatles"
	// and "TheBeatles" stay at distance zero instead of being penalized for spacing alone.
	private static int[] normalize(String s) {
		StringBuilder folded = new StringBuilder(s.length());
		s.toLowerCase(Locale.ROOT)
		 .codePoints()
		 .forEach(
		         cp -> {
			         String replacement = EXTRA_LATIN_FOLDS.get(cp);
			         if (replacement != null) {
				         folded.append(replacement);
			         } else {
				         folded.appendCodePoint(cp);
			         }
		         });
		String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD);
		return decomposed
		                 .codePoints()
		                 .filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK && Character.isLetterOrDigit(cp))
		                 .toArray();
	}

	private static int rawDistance(int[] s1, int[] s2) {
		int[] previousRow = new int[s2.length + 1];
		int[] currentRow = new int[s2.length + 1];

		for (int j = 0; j <= s2.length; j++) {
			previousRow[j] = j;
		}

		for (int i = 1; i <= s1.length; i++) {
			currentRow[0] = i;
			for (int j = 1; j <= s2.length; j++) {
				int cost = s1[i - 1] == s2[j - 1] ? 0 : 1;
				currentRow[j] = Math.min(
				        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1), previousRow[j - 1] + cost);
			}
			int[] swap = previousRow;
			previousRow = currentRow;
			currentRow = swap;
		}

		return previousRow[s2.length];
	}
}
