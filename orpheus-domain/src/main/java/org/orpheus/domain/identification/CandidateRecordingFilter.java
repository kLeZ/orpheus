package org.orpheus.domain.identification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.matching.NormalizedLevenshtein;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.Track;

/**
 * Discards the unrelated recordings linked to the fingerprint of a track before their releases reach
 * {@link ReleaseSelector} (ADR-0018). An AcoustID cluster with score 1.0 can contain recordings of
 * completely different songs and artists (measured on the real run of 2026-09-26: "Bed of Roses" by
 * Bon Jovi also linked to Andy Williams and to a baroque Canon), so the score alone separates nothing.
 * Rules, in order:
 *
 * <ol>
 * <li>recording duration differing from the file's by more than {@link #DURATION_TOLERANCE_SECONDS}:
 * discarded; duration unknown on either side: not discarded;
 * <li>track artist known: only the recordings with a matching credited artist survive
 * ({@link ArtistComparison}); a recording without known artists cannot be verified and is discarded;
 * among the matching ones, if a title has a strict majority only those remain;
 * <li>no recording matching the artist, or artist unknown: the identity (title and artists) that
 * makes up at least half of the remaining candidates survives, otherwise none. If the artist was
 * known, the result states the contrast (ADR-0018 point C, user choice: a consistent cluster is worth
 * more than a tag, downloaded files are often mislabeled).
 * </ol>
 */
public final class CandidateRecordingFilter {

	// Measured on the real run (ADR-0018): 5 s lost 5 correct identifications, 10 s one, from 15 s up
	// none.
	static final int DURATION_TOLERANCE_SECONDS = 15;


	private CandidateRecordingFilter() {
	}

	/**
	 * {@code artistContrastingTag} is present only when the survivors come from the dominant identity
	 * of the cluster despite a known artist in the tag that the cluster does not contain.
	 * {@code fallback} is the dominant identity of the rest of the cluster, to be used only if the
	 * survivors of the tag artist lead to no release (recordings present only on undated bootlegs or
	 * on no release at all: "Holy Diver" by Ronnie James Dio, "Kung Fu Fightin" by the Foo Fighters on
	 * the real run), with its own contrast.
	 */
	public record Result(List<CandidateRecording> survivors,
	                     Optional<String> artistContrastingTag,
	                     Optional<Result> fallback) {

		public Result {
			Objects.requireNonNull(survivors, "survivors");
			Objects.requireNonNull(artistContrastingTag, "artistContrastingTag");
			Objects.requireNonNull(fallback, "fallback");
			survivors = List.copyOf(survivors);
		}

		public Result(List<CandidateRecording> survivors, Optional<String> artistContrastingTag) {
			this(survivors, artistContrastingTag, Optional.empty());
		}

		static Result none() {
			return new Result(List.of(), Optional.empty());
		}
	}

	public static Result filter(Track track, FingerprintLookupResult result) {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(result, "result");

		List<CandidateRecording> compatible = result.candidates()
		                                            .stream()
		                                            .filter(r -> compatibleDuration(r, result.fileDurationSeconds()))
		                                            .toList();
		if (compatible.isEmpty()) {
			return Result.none();
		}

		Optional<String> trackArtist = usableArtist(track);
		// Without an artist tag, the top-level folder ("Skunk Anansie/") chooses among the recordings of
		// the cluster, but it is not enough to declare a contrast: if none carries it, rule 3 without an
		// artist applies.
		Optional<String> artistToChooseBy = trackArtist.or(() -> Optional.ofNullable(track.artistFromPath())
		                                                                 .filter(a -> !Placeholder.missingArtist(a)));
		if (artistToChooseBy.isPresent()) {
			List<CandidateRecording> matching = compatible.stream()
			                                              .filter(r -> sameArtist(List.of(artistToChooseBy.get()), r.artists()))
			                                              .toList();
			if (!matching.isEmpty()) {
				// With a known title, the recordings of the same artist with that title win over the other
				// songs of the artist in the cluster ("In The End" had ended up as "The Untitled" on the real
				// run); if none carries it, the fingerprint has the last word (mislabeled file).
				List<CandidateRecording> withTitle = withKnownTitle(matching, track);
				List<CandidateRecording> others = compatible.stream().filter(r -> !matching.contains(r)).toList();
				Result fallback = dominantAgainst(others, track, trackArtist);
				return new Result(withoutIsolatedAssociations(majorityTitle(withTitle.isEmpty() ? matching : withTitle)), Optional.empty(), fallback.survivors()
				                                                                                                                                    .isEmpty() ? Optional.empty() : Optional.of(fallback));
			}
		}
		return dominantAgainst(compatible, track, trackArtist);
	}

	// Rule 3: the dominant identity of the cluster, possibly against the tag artist.
	private static Result dominantAgainst(List<CandidateRecording> compatible, Track track, Optional<String> trackArtist) {

		// Against a known tag only an identity with known artists counts: a group of candidates without
		// metadata is not a credible alternative to what the source says.
		List<CandidateRecording> base = trackArtist.isPresent() ? compatible.stream()
		                                                                    .filter(r -> !r.artists().isEmpty())
		                                                                    .toList() : compatible;
		if (base.isEmpty()) {
			return Result.none();
		}
		// With a known title only the recordings with that title count, grouped by artist: title variants
		// of the same song ("Like a Drug" and "Like a Drug (instrumental)" by the Desert Sessions) and
		// credit variants ("Bill Haley" and "Bill Haley & His Comets") no longer split the majority.
		List<CandidateRecording> withTitle = withKnownTitle(base, track);
		if (!withTitle.isEmpty()) {
			base = withTitle;
		}
		// Overriding a known tag needs a strict majority: on the real run 6 ties of 1 to 1 would have
		// replaced the tag artist on the mere order of the AcoustID results.
		List<CandidateRecording> dominant = withTitle.isEmpty() ? dominantIdentity(base, trackArtist.isPresent()) : dominantArtist(base, trackArtist.isPresent());
		if (dominant.isEmpty()) {
			return Result.none();
		}
		// Overriding the tag artist requires at least a matching title, when known: "Sugar Sugar" -> The
		// Archies yes, "Take a look around" -> "Mission Impossible" by Dream Theater no.
		if (trackArtist.isPresent() && knownTitle(track) != null && withKnownTitle(dominant, track).isEmpty()) {
			return Result.none();
		}
		Optional<String> contrast = trackArtist.isPresent() ? Optional.of(String.join(", ", dominant.get(0)
		                                                                                            .artists())) : Optional.empty();
		return new Result(withoutIsolatedAssociations(dominant), contrast);
	}

	private static @Nullable String knownTitle(Track track) {
		String title = track.metadata().title();
		return title == null || identityKey(title).isEmpty() ? null : title;
	}

	private static List<CandidateRecording> withKnownTitle(List<CandidateRecording> candidates, Track track) {
		String title = knownTitle(track);
		if (title == null) {
			return List.of();
		}
		String t = identityKey(title);
		// Also without the parenthesized parts of the local title: "Surfing USA (Punk Cover)" is
		// "Surfin' USA".
		String withoutParentheses = identityKey(title.replaceAll("[(\\[][^)\\]]*[)\\]]", " "));
		List<CandidateRecording> similar = candidates.stream()
		                                             .filter(r -> r.title() != null && (similarTitles(t, identityKey(r.title())) || similarTitles(withoutParentheses, identityKey(r.title()))))
		                                             .toList();
		// A version declared in the recording title and absent from the local one loses: "Because of
		// you" is not "Because of You (acoustic version @ 3FM Holland)". An identical title alone is not
		// enough as a criterion: "The Bard's Song" is on the album as "The Bard's Song (In the Forest)"
		// (real run of 2026-10-04).
		String local = title.toLowerCase(Locale.ROOT);
		List<CandidateRecording> withoutOtherVersions = similar.stream()
		                                                       .filter(r -> VERSION_QUALIFIERS.stream()
		                                                                                      .noneMatch(q -> containsWord(r.title(), q) && !containsWord(local, q)))
		                                                       .toList();
		return withoutOtherVersions.isEmpty() ? similar : withoutOtherVersions;
	}

	private static final List<String> VERSION_QUALIFIERS = List.of("acoustic", "live", "demo", "remix", "mix", "instrumental", "edit", "version", "unplugged", "karaoke", "remaster", "remastered", "radio");

	/** True if the candidate title declares a version (live, acoustic...) the local title does not have. */
	static boolean otherVersion(String localTitle, String candidateTitle) {
		String local = localTitle.toLowerCase(Locale.ROOT);
		return VERSION_QUALIFIERS.stream()
		                         .anyMatch(q -> containsWord(candidateTitle, q) && !containsWord(local, q));
	}

	private static boolean containsWord(String text, String word) {
		return java.util.Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).contains(word);
	}

	// Similar, or one contained in the other: "Sanitarium" and "Welcome Home (Sanitarium)".
	private static boolean similarTitles(String a, String b) {
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.contains(b) || b.contains(a) || NormalizedLevenshtein.distance(a, b) <= 0.3;
	}

	private static boolean compatibleDuration(CandidateRecording r, Optional<Integer> fileDuration) {
		if (r.durationSeconds().isEmpty() || fileDuration.isEmpty()) {
			return true;
		}
		return Math.abs(r.durationSeconds().get() - fileDuration.get()) <= DURATION_TOLERANCE_SECONDS;
	}

	private static Optional<String> usableArtist(Track track) {
		String artist = track.metadata().artist();
		if (Placeholder.missingArtist(artist)) {
			return Optional.empty();
		}
		return Optional.of(artist);
	}

	// Rule 11 (user decision of 2026-10-04): a recording associated with the fingerprint few times
	// compared with the most associated one is a wrong link. "Sliver": studio 2297 associations, live at
	// the Paramount 2; the live version won on the bonus disc of "Nevermind". The first version, on the
	// closest duration, moved "Holy Diver", "Hey Jude" and about twenty songs onto compilations:
	// MusicBrainz often has several recordings of the same master with durations 2-4 s apart.
	static final int ISOLATED_ASSOCIATIONS = 10;

	private static List<CandidateRecording> withoutIsolatedAssociations(List<CandidateRecording> candidates) {
		int max = candidates.stream()
		                    .flatMap(r -> r.associations().stream())
		                    .mapToInt(Integer::intValue)
		                    .max()
		                    .orElse(0);
		return candidates.stream()
		                 .filter(r -> r.associations().isEmpty() || r.associations().get() >= ISOLATED_ASSOCIATIONS || r
		                                                                                                                .associations()
		                                                                                                                .get() * 100 >= max)
		                 .toList();
	}

	private static List<CandidateRecording> dominantArtist(List<CandidateRecording> candidates, boolean strictMajority) {
		List<List<CandidateRecording>> groups = new ArrayList<>();
		for (CandidateRecording r : candidates) {
			List<CandidateRecording> group = groups.stream()
			                                       .filter(g -> sameArtist(g.get(0).artists(), r.artists()))
			                                       .findFirst()
			                                       .orElse(null);
			if (group == null) {
				group = new ArrayList<>();
				groups.add(group);
			}
			group.add(r);
		}
		List<CandidateRecording> best = List.of();
		for (List<CandidateRecording> group : groups) {
			if (group.size() > best.size()) {
				best = group;
			}
		}
		boolean enough = strictMajority ? best.size() * 2 > candidates.size() : best.size() * 2 >= candidates.size();
		return enough ? best : List.of();
	}

	// Same artist also when one credit extends the other ("Bill Haley" and "Bill Haley & His Comets").
	private static boolean sameArtist(List<String> a, List<String> b) {
		if (a.isEmpty() || b.isEmpty()) {
			return a.isEmpty() && b.isEmpty();
		}
		for (String x : a) {
			if (ArtistComparison.matches(x, b)) {
				return true;
			}
			for (String y : b) {
				if (extendsCredit(x, y) || extendsCredit(y, x)) {
					return true;
				}
			}
		}
		return false;
	}

	// The long credit starts with the short one followed by a word boundary: "Bill Haley" and "Bill
	// Haley & His Comets" yes, "Queen" and "Queens of the Stone Age" no.
	private static boolean extendsCredit(String longer, String shorter) {
		String l = longer.strip().toLowerCase(Locale.ROOT);
		String s = shorter.strip().toLowerCase(Locale.ROOT);
		if (s.length() < 4 || l.length() <= s.length() || !l.startsWith(s)) {
			return false;
		}
		return !Character.isLetterOrDigit(l.charAt(s.length()));
	}

	private static List<CandidateRecording> dominantIdentity(List<CandidateRecording> candidates, boolean strictMajority) {
		Map<String, List<CandidateRecording>> byIdentity = new LinkedHashMap<>();
		for (CandidateRecording r : candidates) {
			String key = identityKey(r.title()) + "|" + identityKey(String.join(" ", r.artists()));
			byIdentity.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
		}
		List<CandidateRecording> best = List.of();
		for (List<CandidateRecording> group : byIdentity.values()) {
			if (group.size() > best.size()) {
				best = group;
			}
		}
		// At least half of the candidates: a cluster spread over many performers (typical of covers) has
		// no reliable identity.
		boolean enough = strictMajority ? best.size() * 2 > candidates.size() : best.size() * 2 >= candidates.size();
		return enough ? best : List.of();
	}

	// Among the recordings of the same artist the cluster often contains other songs of theirs with a
	// similar duration (real run: "It's My Life" also linked to "All About Lovin' You" and "Come Back").
	// Only a strict majority decides: on a tie all are kept, as before the filter.
	private static List<CandidateRecording> majorityTitle(List<CandidateRecording> matching) {
		Map<String, List<CandidateRecording>> byTitle = new LinkedHashMap<>();
		for (CandidateRecording r : matching) {
			byTitle.computeIfAbsent(identityKey(r.title()), k -> new ArrayList<>()).add(r);
		}
		for (List<CandidateRecording> group : byTitle.values()) {
			if (group.size() * 2 > matching.size()) {
				return group;
			}
		}
		return matching;
	}

	private static String identityKey(String s) {
		if (s == null) {
			return "";
		}
		String decomposed = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
		StringBuilder key = new StringBuilder();
		decomposed.codePoints().filter(Character::isLetterOrDigit).forEach(key::appendCodePoint);
		return key.toString();
	}
}
