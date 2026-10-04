package org.orpheus.domain.ingest;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;

/**
 * Canonical name of an artist as MusicBrainz writes it, derived from the tracks identified by Tier 2
 * (corrective plan, phase 2). It makes the identified and the unidentified tracks of the same artist
 * end up in the same folder: without it, "ACDC/" (unidentified tracks, name from the source) and
 * "AC-DC/" (tracks placed from the release) would live side by side. A local name enters the registry
 * only if it matches the release credit ({@link ArtistComparison}): a contrast with the tag (ADR-0018
 * point C) does not rename the tag artist.
 */
public final class CanonicalArtistNames {

	private final Map<String, Map<String, Integer>> counts = new HashMap<>();
	private final Map<String, Map<String, Integer>> localVariants = new HashMap<>();

	public static CanonicalArtistNames empty() {
		return new CanonicalArtistNames();
	}

	public void register(@Nullable String localName, @Nullable String releaseCredit) {
		if (localName == null || localName.isBlank() || releaseCredit == null || releaseCredit.isBlank()) {
			return;
		}
		if (!ArtistComparison.matches(localName, List.of(releaseCredit))) {
			return;
		}
		counts.computeIfAbsent(key(localName), k -> new HashMap<>())
		      .merge(releaseCredit.strip(), 1, Integer::sum);
	}

	/**
	 * Registers a name as it appears in the source: among variants differing only in capitalization
	 * ("Pain Of Salvation", "Pain of Salvation", "Green Day", "Greenday") the folder uses the most
	 * frequent one.
	 */
	public void registerLocal(@Nullable String localName) {
		if (localName == null || localName.isBlank()) {
			return;
		}
		localVariants.computeIfAbsent(key(localName), k -> new HashMap<>())
		             .merge(localName.strip(), 1, Integer::sum);
	}

	/**
	 * The most frequent canonical name for this local name; failing that, the most frequent local
	 * variant; otherwise the name itself.
	 */
	public String canonical(String localName) {
		Objects.requireNonNull(localName, "localName");
		Map<String, Integer> candidates = counts.get(key(localName));
		if (candidates == null || candidates.isEmpty()) {
			candidates = localVariants.get(key(localName));
		}
		if (candidates == null || candidates.isEmpty()) {
			return localName;
		}
		return candidates.entrySet()
		                 .stream()
		                 .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
		                 .orElseThrow()
		                 .getKey();
	}

	// Only letters and digits, without diacritics: "Blink 182", "blink‐182", "Greenday" and "Green
	// Day" are the same key.
	private static String key(String name) {
		String decomposed = Normalizer.normalize(name.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
		StringBuilder key = new StringBuilder();
		decomposed.codePoints().filter(Character::isLetterOrDigit).forEach(key::appendCodePoint);
		return key.toString();
	}
}
