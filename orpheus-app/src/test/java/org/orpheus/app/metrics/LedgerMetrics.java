package org.orpheus.app.metrics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.model.Placeholder;

/**
 * Metrics of the damage of the real run (corrective plan, docs/ROADMAP.md) computed on the ledger of a
 * run: one row per ledger entry, plus the tag artist of the source file. No dependency on Quarkus: the
 * rows come from whoever reads the database.
 */
public final class LedgerMetrics {

	public record Entry(Path source, Path destination, String action, String reason) {
	}

	public record Result(int entries,
	                     int fakeAlbums,
	                     int numericSuffixes,
	                     int unknownTracks,
	                     int unidentifiedWithRelease,
	                     int conflictingYears,
	                     int tracksInAlbumsWithDifferentArtists,
	                     int releasesByUnrelatedArtists,
	                     Map<String, List<String>> examples) {
	}

	static final int EXAMPLES_PER_METRIC = 10;

	private static final Pattern NUMERIC_SUFFIX = Pattern.compile(" \\(\\d+\\)\\.[^.]+$");
	private static final Pattern YEAR_IN_FOLDER = Pattern.compile("(?<!\\d)(19[5-9]\\d|20[0-3]\\d)(?!\\d)");
	private static final Pattern ALBUM_FOLDER = Pattern.compile("^(.+?) - (.+)$");
	private static final Pattern RELEASE_WITH_ARTISTS = Pattern.compile("on release \\S+ by \"([^\"]*)\"");
	private static final Pattern CREDIT_SEPARATORS = Pattern.compile("\\s*(?:&|,|\\bfeat\\.?|\\bft\\.)\\s*", Pattern.CASE_INSENSITIVE);
	private static final String UNKNOWN_TRACK = "Unknown Track";
	private static final String UNIDENTIFIED = "Unidentified";
	private static final String VARIOUS_ARTISTS = "various artists";


	private LedgerMetrics() {
	}

	public static Result compute(List<Entry> entries, Path source, Path destination, Function<Path, @Nullable String> tagArtistOf) {
		int fakeAlbums = 0;
		int suffixes = 0;
		int unknown = 0;
		int unidentifiedWithRelease = 0;
		int conflictingYears = 0;
		int unrelatedReleases = 0;
		Map<Path, List<String>> artistsByAlbum = new LinkedHashMap<>();
		Map<String, List<String>> examples = new LinkedHashMap<>();

		for (Entry m : entries) {
			Path relSource = source.relativize(m.source());
			Path relDestination = destination.relativize(m.destination());
			String fileName = relDestination.getFileName().toString();
			String tagArtist = tagArtistOf.apply(m.source());

			if (NUMERIC_SUFFIX.matcher(fileName).find()) {
				suffixes++;
			}
			if (fileName.contains(UNKNOWN_TRACK)) {
				unknown++;
			}
			boolean inUnidentified = relDestination.getNameCount() >= 2 && relDestination.getName(1)
			                                                                             .toString()
			                                                                             .equals(UNIDENTIFIED);
			if (inUnidentified && m.reason().contains("on release ")) {
				unidentifiedWithRelease++;
			}

			Matcher release = RELEASE_WITH_ARTISTS.matcher(m.reason());
			if (release.find() && !m.reason().contains("contrasting") && usable(tagArtist)) {
				List<String> credits = List.of(release.group(1).split(", "));
				// A Various Artists compilation is the only release of the recording: the track stays under its
				// artist, it is not an unrelated artist.
				boolean compilation = credits.stream().anyMatch(c -> c.equalsIgnoreCase("Various Artists"));
				if (!compilation && !ArtistComparison.matches(tagArtist, credits)) {
					unrelatedReleases++;
					example(examples, "release by an unrelated artist", relSource + " [tag: " + tagArtist + "] -> " + release.group(1));
				}
			}

			if (inUnidentified || relDestination.getNameCount() < 3) {
				continue;
			}
			String folderArtist = relDestination.getName(0).toString();
			Matcher album = ALBUM_FOLDER.matcher(relDestination.getName(1).toString());
			if (!album.matches()) {
				continue;
			}
			String destinationYear = album.group(1);
			String albumTitle = album.group(2);

			// The title of a Tier 2 entry comes from the release: a real same-named album ("Foo Fighters",
			// 1995) is not a fake album.
			if (!m.reason().contains("on release ") && fakeAlbum(albumTitle, folderArtist, tagArtist, relSource)) {
				fakeAlbums++;
				example(examples, "fake albums", relSource + " -> " + relDestination);
			}
			String sourceYear = yearInTheFolderOfTheFile(relSource);
			if (sourceYear != null && !sourceYear.equals(destinationYear)) {
				conflictingYears++;
				example(examples, "conflicting year", relSource + " -> " + relDestination);
			}
			if (!folderArtist.toLowerCase(Locale.ROOT).equals(VARIOUS_ARTISTS) && usable(tagArtist)) {
				artistsByAlbum.computeIfAbsent(relDestination.subpath(0, 2), k -> new ArrayList<>()).add(tagArtist);
			}
		}

		int tracksInMixedAlbums = 0;
		for (var entry : artistsByAlbum.entrySet()) {
			List<String> artists = entry.getValue();
			if (artistGroups(artists) > 1) {
				tracksInMixedAlbums += artists.size();
				example(examples, "albums with different artists", entry.getKey() + " " + new LinkedHashSet<>(artists));
			}
		}
		return new Result(entries.size(), fakeAlbums, suffixes, unknown, unidentifiedWithRelease, conflictingYears, tracksInMixedAlbums, unrelatedReleases, examples);
	}

	private static void example(Map<String, List<String>> examples, String metric, String line) {
		List<String> list = examples.computeIfAbsent(metric, k -> new ArrayList<>());
		if (list.size() < EXAMPLES_PER_METRIC) {
			list.add(line);
		}
	}

	// The file lies directly in a top-level folder (e.g. "Red Hot Chili Peppers/05.mp3") and the title of
	// the destination album is the artist name: it is the artist folder promoted to album. A real
	// same-named album in its own folder ("Black Sabbath/Black Sabbath/") does not count, nor does a
	// top-level album folder ("Crossing All Over! Volume 13/"). Known limit: since phase 2 a single placed
	// back on the real same-named album (e.g. "Foo Fighters", 1995) would still count.
	private static boolean fakeAlbum(String title, String folderArtist, @Nullable String tagArtist, Path relSource) {
		if (relSource.getNameCount() != 2) {
			return false;
		}
		String t = normalize(title);
		return t.equals(normalize(folderArtist)) || (tagArtist != null && t.equals(normalize(tagArtist)));
	}

	// First year in the name of the folder containing the file: "News Of The World (1977) (Remastered
	// 1993)" is from 1977. With the last year of all the folders the metric counted 41 tracks with the
	// right year as conflicts.
	private static @Nullable String yearInTheFolderOfTheFile(Path relSource) {
		if (relSource.getNameCount() < 2) {
			return null;
		}
		Matcher m = YEAR_IN_FOLDER.matcher(relSource.getName(relSource.getNameCount() - 2).toString());
		return m.find() ? m.group(1) : null;
	}

	// A featuring ("Queen & David Bowie" in a Queen album) is not another artist: the credits are split into
	// parts and two artists are in the same group if they share at least one part.
	private static int artistGroups(List<String> artists) {
		List<List<String>> representatives = new ArrayList<>();
		for (String a : artists) {
			List<String> parts = parts(a);
			if (representatives.stream().noneMatch(r -> parts.stream().anyMatch(p -> ArtistComparison.matches(p, r)))) {
				representatives.add(parts);
			}
		}
		return representatives.size();
	}

	private static List<String> parts(String credit) {
		return List.of(CREDIT_SEPARATORS.split(credit)).stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
	}

	private static boolean usable(@Nullable String artist) {
		return !Placeholder.missingArtist(artist);
	}

	private static String normalize(String s) {
		return s.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
	}
}
