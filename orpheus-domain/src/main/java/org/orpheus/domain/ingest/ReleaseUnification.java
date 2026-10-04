package org.orpheus.domain.ingest;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;

/**
 * Tracks identified on different releases of the same album (same artist credit, same title) end up in
 * the folder of the release with the most tracks, the oldest on a tie (ADR-0020). On the real run: 10
 * tracks of "Ecliptica" by Sonata Arctica placed by the consensus on the 1999 release and 2 identified
 * one by one on the 2017 reissue ended up in two folders. Number and title of the track stay those of
 * its release, the disc is lost; year, folder title and release id become those of the dominant
 * release.
 */
public final class ReleaseUnification {

	private final Map<String, Release> dominantByKey = new HashMap<>();
	private final Map<String, Set<String>> titlesByKey = new HashMap<>();

	private ReleaseUnification() {
	}

	public static ReleaseUnification of(List<Release> assignedReleases) {
		Objects.requireNonNull(assignedReleases, "assignedReleases");
		Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
		Map<String, Release> byRelease = new HashMap<>();
		ReleaseUnification u = new ReleaseUnification();
		for (Release e : assignedReleases) {
			if (e.info() == null || e.info().title().isBlank()) {
				continue;
			}
			String key = key(e.info());
			counts.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(e.releaseId(), 1, Integer::sum);
			byRelease.putIfAbsent(e.releaseId(), e);
			if (e.info().trackTitle() != null) {
				u.titlesByKey.computeIfAbsent(key, k -> new HashSet<>()).add(normalize(e.info().trackTitle()));
			}
		}
		for (var entry : counts.entrySet()) {
			Comparator<Map.Entry<String, Integer>> byTracks = Comparator.comparingInt(Map.Entry::getValue);
			Comparator<Map.Entry<String, Integer>> oldest = (a, b) -> byRelease.get(b.getKey())
			                                                                   .releaseDate()
			                                                                   .compareTo(byRelease.get(a.getKey())
			                                                                                       .releaseDate());
			Release dominant = byRelease.get(entry.getValue()
			                                      .entrySet()
			                                      .stream()
			                                      .max(byTracks.thenComparing(oldest))
			                                      .orElseThrow()
			                                      .getKey());
			u.dominantByKey.put(entry.getKey(), dominant);
		}
		return u;
	}

	/** The release with year, title and release id of the dominant release of its album. */
	public Release unified(Release e) {
		if (e.info() == null || e.info().title().isBlank()) {
			return e;
		}
		Release dominant = dominantByKey.get(key(e.info()));
		if (dominant == null || dominant.releaseId().equals(e.releaseId())) {
			return e;
		}
		ReleaseInfo i = e.info();
		// The disc of another release does not hold in this one (a bonus disc of the 2017 reissue is not
		// disc 2 of the 1999 release): without this, the album would look multi-disc.
		ReleaseInfo info = i.asEdition(dominant.info().title());
		return new Release(dominant.releaseId(), e.type(), dominant.releaseDate(), e.artists(), e.secondary(), info);
	}

	/**
	 * Year of the dominant release for a Tier 1 album with the same artist and title ("Sonata
	 * Arctica/Ecliptica/", tag year 2003, next to "Ecliptica" placed by Tier 2 on 1999): one folder
	 * instead of two. At least one song in common is needed: an artist can have several albums with the
	 * same name ("Weezer" of 1994 and of 2001).
	 */
	public Optional<Integer> yearFor(@Nullable String artist, @Nullable String title, List<String> trackTitles) {
		return dominantFor(artist, title, trackTitles, r -> List.of()).map(e -> e.releaseDate().year());
	}

	/**
	 * The dominant release for a Tier 1 album with the same artist and title, from which the album takes
	 * year and title spelling, if the album has at least one song in its tracklist (rule 8, user decision
	 * of 2026-10-04): a partial album often has tracks identified by Tier 2 and tracks grouped by tag
	 * with no songs in common ("The Number of the Beast" and "The Number Of The Beast", 8 albums on the
	 * run of 2026-10-04), and a remaster has to be unified however many years apart. Two same-named
	 * albums with different songs ("Weezer" of 1994 and of 2001) stay distinct.
	 *
	 * @param tracklist track titles of a release, empty if unknown
	 */
	public Optional<Release> dominantFor(@Nullable String artist, @Nullable String title, List<String> trackTitles, java.util.function.Function<String, List<String>> tracklist) {
		if (artist == null || title == null) {
			return Optional.empty();
		}
		String key = normalize(artist) + "|" + normalize(title);
		Release dominant = dominantByKey.get(key);
		if (dominant == null) {
			return Optional.empty();
		}
		Set<String> titles = new HashSet<>(titlesByKey.getOrDefault(key, Set.of()));
		List<String> local = trackTitles.stream().map(ReleaseUnification::normalize).toList();
		if (local.stream().noneMatch(titles::contains)) {
			tracklist.apply(dominant.releaseId()).stream().map(ReleaseUnification::normalize).forEach(titles::add);
		}
		return local.stream().anyMatch(titles::contains) ? Optional.of(dominant) : Optional.empty();
	}

	private static String key(ReleaseInfo info) {
		return normalize(info.artistCredit()) + "|" + normalize(info.title());
	}

	private static String normalize(String s) {
		StringBuilder k = new StringBuilder();
		Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
		          .codePoints()
		          .filter(Character::isLetterOrDigit)
		          .forEach(k::appendCodePoint);
		return k.toString();
	}
}
