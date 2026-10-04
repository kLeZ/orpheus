package org.orpheus.domain.identification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.Track;

/**
 * Tier 0/1 local identification (SPEC.md section 4.4): no external source is consulted, so every
 * {@link Album} produced has {@link AlbumCompleteness#UNVERIFIED}. Tier 1, clustering by album
 * tag, takes precedence over Tier 0: a track with a usable album tag goes through tag clustering, the
 * others through the fallback on the source folder name, unless they share the folder with tracks
 * that do have a real tag, in which case they inherit it (see {@link #identify}).
 *
 * <p>The source folder of a track is {@code provenance().source().getParent()}, not
 * {@code physicalPath()}: the latter may already reflect a destination, while the provenance stays the
 * reliable snapshot of where the file really comes from.
 */
public final class LocalIdentifier {

	private static final String VARIOUS_ARTISTS = "Various Artists";

	private LocalIdentifier() {
	}

	private record FolderTagKey(@Nullable Path folder, String normalizedTitle) {
	}

	private record Subgroup(String normalizedTitle, @Nullable String settledArtist, List<Track> tracks) {
	}

	private record ClusterKey(String normalizedTitle, String normalizedArtist) {
	}

	private record TaggedTrack(Track track, String effectiveAlbumTag) {
	}

	public static LocalIdentificationResult identify(Collection<Track> tracks) {
		return identify(tracks, null);
	}

	/**
	 * @param root root of the source: the files lying directly in it never form an album by folder (on
	 *             the real run the root of ~/Musica mixed Sonata Arctica and Elio e le Storie Tese); if
	 *             null, no rule on the root
	 */
	public static LocalIdentificationResult identify(Collection<Track> tracks, @Nullable Path root) {
		Objects.requireNonNull(tracks, "tracks");
		Path normalizedRoot = root == null ? null : root.toAbsolutePath().normalize();

		Map<Path, List<Track>> byFolder = new LinkedHashMap<>();
		List<Track> withoutFolder = new ArrayList<>();
		List<Track> isolatedInRoot = new ArrayList<>();
		List<TaggedTrack> taggedInRoot = new ArrayList<>();
		for (Track t : tracks) {
			Path folder = sourceFolder(t);
			if (folder == null) {
				withoutFolder.add(t);
			} else if (normalizedRoot != null && folder.toAbsolutePath().normalize().equals(normalizedRoot)) {
				if (hasUsableAlbumTag(t)) {
					taggedInRoot.add(new TaggedTrack(t, t.metadata().album()));
				} else {
					isolatedInRoot.add(t);
				}
			} else {
				byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(t);
			}
		}

		// A folder where some tracks are tagged and others are not (partial curation, tags half
		// written) must not split into two distinct Albums when the evidence is not ambiguous: if the
		// folder has a single real album tag, the untagged tracks inherit it instead of ending up
		// isolated or on a separate folder fallback. If several different real tags appear in the same
		// folder (rare but possible), nobody guesses who inherits what: the original per-track behavior
		// is kept.
		List<TaggedTrack> withAlbumTag = new ArrayList<>(taggedInRoot);
		List<Track> withoutAlbumTag = new ArrayList<>(withoutFolder);
		for (List<Track> group : byFolder.values()) {
			Map<String, String> realTagsInGroup = new LinkedHashMap<>();
			List<String> taggedArtists = new ArrayList<>();
			for (Track t : group) {
				if (hasUsableAlbumTag(t)) {
					realTagsInGroup.putIfAbsent(normalize(t.metadata().album()), t.metadata().album());
					if (!Placeholder.missingArtist(t.metadata().artist())) {
						taggedArtists.add(t.metadata().artist());
					}
				}
			}
			if (realTagsInGroup.size() == 1) {
				String dominantTag = realTagsInGroup.values().iterator().next();
				for (Track t : group) {
					if (hasUsableAlbumTag(t)) {
						withAlbumTag.add(new TaggedTrack(t, t.metadata().album()));
					} else if (compatibleArtist(t, taggedArtists)) {
						withAlbumTag.add(new TaggedTrack(t, dominantTag));
					} else {
						withoutAlbumTag.add(t);
					}
				}
			} else {
				for (Track t : group) {
					if (hasUsableAlbumTag(t)) {
						withAlbumTag.add(new TaggedTrack(t, t.metadata().album()));
					} else {
						withoutAlbumTag.add(t);
					}
				}
			}
		}

		List<Album> albums = new ArrayList<>();
		List<Track> isolated = new ArrayList<>(isolatedInRoot);
		groupByAlbumTag(withAlbumTag, albums);
		groupByFolder(withoutAlbumTag, albums, isolated);

		return new LocalIdentificationResult(albums, isolated);
	}

	// Tier 1. Grouping first by (folder, normalized album tag): it is the scope where SPEC.md 4.3
	// evaluates the local Various Artists rule ("same folder, same album tag"), so that is where the
	// album artist of each subgroup has to be settled. Only afterwards are the subgroups reconciled
	// across folders by consistent tag: merging first by title alone would fuse two same-named albums
	// of different artists (e.g. two distinct "Greatest Hits") under a single invented Various Artists
	// label, a falsehood Tier 2 could no longer fix because the resulting album would already have real
	// tags.
	private static void groupByAlbumTag(List<TaggedTrack> tracks, List<Album> result) {
		Map<FolderTagKey, List<Track>> byFolderAndTag = new LinkedHashMap<>();
		Map<String, String> originalTitle = new LinkedHashMap<>();
		for (TaggedTrack tt : tracks) {
			String albumTag = tt.effectiveAlbumTag();
			String normalizedTitle = normalize(albumTag);
			var key = new FolderTagKey(sourceFolder(tt.track()), normalizedTitle);
			byFolderAndTag.computeIfAbsent(key, k -> new ArrayList<>()).add(tt.track());
			originalTitle.putIfAbsent(normalizedTitle, albumTag.trim());
		}

		List<Subgroup> subgroups = new ArrayList<>();
		for (var entry : byFolderAndTag.entrySet()) {
			String settledArtist = settleAlbumArtist(entry.getValue());
			subgroups.add(new Subgroup(entry.getKey().normalizedTitle(), settledArtist, entry.getValue()));
		}

		// The clusters with a real identity (non-null artist) are resolved first, in a single pass that
		// does not depend on processing order: grouped by (title, normalized artist), a map key does not
		// suffer from arrival order. Only then is it decided where the subgroups without an artist go,
		// looking at the COMPLETE set of real clusters already resolved for that title, not only those
		// found so far in a sequential scan. That was exactly the bug of the single-scan approach: a
		// subgroup without an artist could end up glued to a real identity only because the other one,
		// equally compatible by title, had not been created yet at its turn; the result depended on the
		// iteration order of the input, not on its content.
		Map<ClusterKey, List<Subgroup>> realClusters = new LinkedHashMap<>();
		List<Subgroup> withoutArtist = new ArrayList<>();
		for (Subgroup sg : subgroups) {
			if (sg.settledArtist() == null) {
				withoutArtist.add(sg);
			} else {
				realClusters.computeIfAbsent(new ClusterKey(sg.normalizedTitle(), normalize(sg.settledArtist())), k -> new ArrayList<>())
				            .add(sg);
			}
		}

		Map<String, List<ClusterKey>> realClustersByTitle = new LinkedHashMap<>();
		for (ClusterKey key : realClusters.keySet()) {
			realClustersByTitle.computeIfAbsent(key.normalizedTitle(), t -> new ArrayList<>()).add(key);
		}

		// Several subgroups without an artist and with the same title are still the same consistent
		// signal (same album tag, Tier 1) and are reconciled together when they cannot attach to a single
		// real identity: it is the base case of Tier 1 itself (tracks with the same tag spread over
		// several folders), not an exception.
		Map<String, List<Subgroup>> orphanClustersByTitle = new LinkedHashMap<>();
		for (Subgroup sg : withoutArtist) {
			List<ClusterKey> realCandidates = realClustersByTitle.getOrDefault(sg.normalizedTitle(), List.of());
			if (realCandidates.size() == 1) {
				realClusters.get(realCandidates.get(0)).add(sg);
			} else {
				orphanClustersByTitle.computeIfAbsent(sg.normalizedTitle(), t -> new ArrayList<>()).add(sg);
			}
		}

		for (List<Subgroup> group : realClusters.values()) {
			addByEdition(group, originalTitle, result);
		}
		for (List<Subgroup> group : orphanClustersByTitle.values()) {
			addByEdition(group, originalTitle, result);
		}
	}

	// Edition qualifier in the folder name: "2002 - Remedy Lane (Japanese Edition)".
	private static final Pattern EDITION_QUALIFIER = Pattern.compile(
	        "[(\\[]([^)\\]]*(?:edition|edizione|bonus|deluxe|limited|expanded|special|anniversary|japan|remaster)[^)\\]]*)[)\\]]", Pattern.CASE_INSENSITIVE);

	// Two folders with the same album tag are the same edition, unless one carries an edition qualifier
	// in its name and contains songs the other lacks (Remedy Lane standard, 13 tracks, and Japanese
	// Edition, 14, on the real run): then they are two distinct albums (ADR-0019). With the same
	// tracklist they are copies, and the best copy is chosen on the destination path.
	private static void addByEdition(List<Subgroup> group, Map<String, String> originalTitle, List<Album> result) {
		List<Subgroup> base = new ArrayList<>();
		Map<String, List<Subgroup>> qualified = new LinkedHashMap<>();
		for (Subgroup sg : group) {
			String qualifier = editionQualifier(sg);
			if (qualifier == null) {
				base.add(sg);
			} else {
				qualified.computeIfAbsent(qualifier, k -> new ArrayList<>()).add(sg);
			}
		}
		if (qualified.isEmpty() || base.isEmpty()) {
			result.add(albumFromCluster(group, originalTitle));
			return;
		}
		Set<String> baseTitles = titles(base);
		for (var entry : qualified.entrySet()) {
			if (baseTitles.containsAll(titles(entry.getValue()))) {
				base.addAll(entry.getValue());
			} else {
				Album edition = albumFromCluster(entry.getValue(), originalTitle);
				result.add(new Album(edition.title() + " (" + entry.getKey() + ")", edition.albumArtist(), edition.year(), edition.completeness(), edition.tracks()));
			}
		}
		result.add(albumFromCluster(base, originalTitle));
	}

	private static @Nullable String editionQualifier(Subgroup sg) {
		Path folder = sourceFolder(sg.tracks().get(0));
		if (folder == null || folder.getFileName() == null) {
			return null;
		}
		Matcher m = EDITION_QUALIFIER.matcher(folder.getFileName().toString());
		return m.find() ? m.group(1).strip() : null;
	}

	private static Set<String> titles(List<Subgroup> subgroups) {
		Set<String> titles = new LinkedHashSet<>();
		for (Subgroup sg : subgroups) {
			for (Track t : sg.tracks()) {
				titles.add(t.metadata().title() == null ? "" : t.metadata()
				                                                .title()
				                                                .replaceAll("[^\\p{L}\\p{N}]", "")
				                                                .toLowerCase(Locale.ROOT));
			}
		}
		return titles;
	}

	private static Album albumFromCluster(List<Subgroup> group, Map<String, String> originalTitle) {
		List<Track> allTracks = new ArrayList<>();
		String albumArtist = null;
		for (Subgroup sg : group) {
			allTracks.addAll(sg.tracks());
			if (albumArtist == null && sg.settledArtist() != null) {
				albumArtist = sg.settledArtist();
			}
		}
		return buildAlbum(originalTitle.get(group.get(0).normalizedTitle()), albumArtist, allTracks);
	}

	// Tier 0. Unlike Tier 1, there is no tag to lean on here: grouping by folder is the only signal
	// available, so at least two tracks are required to take it as a valid album signal. A folder with
	// a single track adds nothing compared with any isolated track: forcing it into a one-track "album"
	// would be an unfounded assertion (an isolated track rightly stays a candidate for a fingerprint
	// identification in Tier 2, DESIGN.md).
	private static void groupByFolder(List<Track> tracks, List<Album> result, List<Track> isolated) {
		Map<Path, List<Track>> byFolder = new LinkedHashMap<>();
		for (Track t : tracks) {
			Path folder = sourceFolder(t);
			if (folder == null) {
				isolated.add(t);
				continue;
			}
			byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(t);
		}

		for (var entry : byFolder.entrySet()) {
			List<Track> group = entry.getValue();
			Path folderName = entry.getKey().getFileName();
			if (group.size() < 2 || folderName == null) {
				isolated.addAll(group);
				continue;
			}
			String albumArtist = settleAlbumArtist(group);
			// An artist folder with singles from different records ("Red Hot Chili Peppers/", 17 tracks on
			// the real run): it is not an album, and promoting it to an album named after the artist
			// produced "YYYY - Red Hot Chili Peppers" folders. The tracks stay isolated: Tier 2 places them
			// one by one back on their original album (corollary 2 of DESIGN.md).
			if (artistFolder(folderName.toString(), albumArtist, group)) {
				isolated.addAll(group);
				continue;
			}
			result.add(buildAlbum(folderName.toString(), albumArtist, group));
		}
	}

	// The folder carries the name of the artist of the group, or of an artist present in the credit of
	// every track ("Dream Theater/" with "Pull Me Under" and a medley "Pink Floyd, Kansas, ... & Dream
	// Theater", which alone would make the group Various Artists).
	private static final Pattern CREDIT_SEPARATORS = Pattern.compile("\\s*(?:&|,|\\bfeat\\.?|\\bft\\.|\\band\\b)\\s*", Pattern.CASE_INSENSITIVE);

	private static boolean artistFolder(String folderName, @Nullable String albumArtist, List<Track> group) {
		if (albumArtist != null && !VARIOUS_ARTISTS.equals(albumArtist) && ArtistComparison.matches(folderName, List.of(albumArtist))) {
			return true;
		}
		for (Track t : group) {
			String artist = t.metadata().artist();
			if (Placeholder.missingArtist(artist)) {
				return false;
			}
			if (List.of(CREDIT_SEPARATORS.split(artist))
			        .stream()
			        .map(String::strip)
			        .noneMatch(p -> !p.isEmpty() && ArtistComparison.matches(folderName, List.of(p)))) {
				return false;
			}
		}
		return true;
	}

	// The most frequent year among the tracks, the oldest on a tie: the first one found depended on the
	// order of the files ("Black Sabbath/Sabotage/ - 03.mp3" with 1973 ahead of seven tracks with 1975
	// gave "1973 - Sabotage", real run of 2026-09-27).
	private static Album buildAlbum(String title, @Nullable String albumArtist, List<Track> tracks) {
		Map<Integer, Long> counts = tracks.stream()
		                                  .map(t -> t.metadata().year())
		                                  .filter(Objects::nonNull)
		                                  .collect(java.util.stream.Collectors.groupingBy(a -> a, java.util.TreeMap::new, java.util.stream.Collectors.counting()));
		Integer year = counts.entrySet()
		                     .stream()
		                     .max(Map.Entry.<Integer, Long>comparingByValue()
		                                   .thenComparing(Map.Entry.comparingByKey(java.util.Comparator.reverseOrder())))
		                     .map(Map.Entry::getKey)
		                     .orElse(null);
		return new Album(title, albumArtist, year, AlbumCompleteness.UNVERIFIED, tracks);
	}

	// A consistent album artist tag over the whole group (when present) is more direct evidence of "who
	// the album artist is" than the variety of the track artists: a stable album artist tag on a record
	// with a different featuring on every song (very common, not an edge case) must not be discarded in
	// favor of the Various Artists rule just because the track artists differ. So it has to be checked
	// BEFORE the Various Artists trigger, not after: checking it after means never checking it, because
	// the trigger would already have returned.
	private static @Nullable String settleAlbumArtist(List<Track> tracks) {
		String consistentAlbumArtist = consistentAlbumArtist(tracks);
		if (consistentAlbumArtist != null) {
			return consistentAlbumArtist;
		}

		Set<String> normalizedTrackArtists = new LinkedHashSet<>();
		for (Track t : tracks) {
			String artist = t.metadata().artist();
			if (!Placeholder.missingArtist(artist)) {
				normalizedTrackArtists.add(normalize(artist));
			}
		}
		if (normalizedTrackArtists.size() >= 2) {
			return VARIOUS_ARTISTS;
		}
		for (Track t : tracks) {
			String artist = t.metadata().artist();
			if (!Placeholder.missingArtist(artist)) {
				return artist.trim();
			}
		}
		return null;
	}

	// Consistent = all the tracks with a non-empty album artist tag agree (after normalization); the
	// tracks without one do not contradict the others (absence of information, not evidence of a
	// conflict). A single disagreeing value makes the whole group inconsistent: it returns null, leaving
	// the decision to the Various Artists trigger on the track artists.
	private static @Nullable String consistentAlbumArtist(List<Track> tracks) {
		String normalized = null;
		String original = null;
		for (Track t : tracks) {
			String albumArtist = t.metadata().albumArtist();
			if (Placeholder.missingAlbumArtist(albumArtist)) {
				continue;
			}
			String n = normalize(albumArtist);
			if (normalized == null) {
				normalized = n;
				original = albumArtist.trim();
			} else if (!normalized.equals(n)) {
				return null;
			}
		}
		return original;
	}

	/**
	 * Single-track Tier 2 gate (SPEC.md 4.4: "empty or placeholder album"): exposes the same logic
	 * already used for the Tier 0 fallback, without duplicating it: an album tag assigned by a folder
	 * fallback is not a real identification, so the track stays a candidate for a fingerprint
	 * identification. Distinct from the multi-track consensus gate (SPEC 4.5, "empty artist" signal,
	 * see {@link MultiTrackConsensus#isEligibleGroup}), which looks at a different tag at group level,
	 * not at single-track level.
	 */
	public static boolean placeholderOrEmptyAlbumTag(Track t) {
		return !hasUsableAlbumTag(t);
	}

	private static boolean hasUsableAlbumTag(Track t) {
		return !Placeholder.missingAlbum(t.metadata().album());
	}

	// A track without an album tag inherits the single one of the folder only if its artist is unknown
	// or compatible with that of the tagged tracks: otherwise an album by another artist that ended up
	// in the same folder takes it (Sonata Arctica inside "L'album Biango" by Elio).
	private static boolean compatibleArtist(Track t, List<String> taggedArtists) {
		String artist = t.metadata().artist();
		if (Placeholder.missingArtist(artist) || taggedArtists.isEmpty()) {
			return true;
		}
		return ArtistComparison.matches(artist, taggedArtists);
	}

	private static @Nullable Path sourceFolder(Track t) {
		Path folder = t.provenance().source().getParent();
		return folder == null ? null : folder.normalize();
	}

	private static String normalize(String s) {
		return s.strip().toLowerCase(Locale.ROOT);
	}
}
