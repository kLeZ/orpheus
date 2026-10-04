package org.orpheus.domain.identification;

import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.Track;

/**
 * Cleanup and completion of the local metadata before identification (corrective plan, phase 1).
 * Valid tags always take precedence; file name and folder name fill in only what is missing:
 *
 * <ul>
 * <li>placeholders ("Artista sconosciuto", "Unknown Album"...) treated as absent values;
 * <li>a year outside a plausible range treated as absent;
 * <li>missing title and track number taken from the file name ({@link FileNameParser});
 * <li>missing year taken from the folder name ("Deep Purple - 1971 - Fireball", "(1998) Sultans Of
 * Swing", "2013 - Days Of Convergence");
 * <li>a track number repeated on several files of the same folder, album and disc is unreliable and is
 * removed (on the real run: the "32" of 7 Linkin Park files);
 * <li>the top-level folder under the root becomes {@link Track#artistFromPath()}.
 * </ul>
 */
public final class LocalEvidence {

	static final int MIN_YEAR = 1900;

	// Only forms where the year is set apart from the rest of the name, so as not to take a title like
	// "1984" or "1999" for a year.
	private static final Pattern YEAR_IN_FOLDER = Pattern.compile("(?:^|\\s-\\s|^\\(|\\s\\(|\\[)((?:19|20)\\d{2})(?:\\s-\\s|\\)|\\]|$)");

	private LocalEvidence() {
	}

	/**
	 * @param root root of the source; if null, no artist from the path
	 */
	public static List<Track> apply(List<Track> tracks, @Nullable Path root) {
		Objects.requireNonNull(tracks, "tracks");
		List<Track> completed = new ArrayList<>(tracks.size());
		for (Track t : tracks) {
			completed.add(complete(t, root));
		}
		return withoutRepeatedNumbers(completed);
	}

	private static Track complete(Track t, @Nullable Path root) {
		TrackMetadata m = t.metadata();
		String artist = Placeholder.missingArtist(m.artist()) ? null : m.artist();
		String albumArtist = Placeholder.missingAlbumArtist(m.albumArtist()) ? null : m.albumArtist();
		String album = Placeholder.missingAlbum(m.album()) ? null : m.album();
		String title = Placeholder.missingTitle(m.title()) ? null : m.title();
		Integer year = plausible(m.year()) ? m.year() : null;
		Integer number = m.trackNumber() != null && m.trackNumber() > 0 ? m.trackNumber() : null;

		String artistFromPath = artistFromPath(t.provenance().source(), root);
		Path file = t.provenance().source();
		if (title == null || number == null) {
			String knownArtist = artist != null ? artist : artistFromPath;
			FileNameParser.Result name = FileNameParser.parse(file.getFileName().toString(), knownArtist);
			if (title == null) {
				title = name.title().orElse(null);
			}
			if (number == null) {
				number = name.number().orElse(null);
			}
		}
		if (file.getParent() != null && file.getParent().getFileName() != null) {
			Integer folderYear = yearFromFolder(file.getParent().getFileName().toString());
			// With two different valid years the older wins, as for releases in ADR-001: the tag often
			// carries the year of a remaster ("Queen/01 Queen (1973)" with tag 1994).
			if (folderYear != null && (year == null || folderYear < year)) {
				year = folderYear;
			}
		}

		TrackMetadata completedMetadata = new TrackMetadata(artist, title, album, albumArtist, year, number, m.discNumber(), m.externalIds());
		return new Track(t.physicalPath(), completedMetadata, t.provenance(), artistFromPath, t.originalMetadata(), t.tagGenres());
	}

	private static boolean plausible(@Nullable Integer year) {
		return year != null && year >= MIN_YEAR && year <= Year.now().getValue() + 1;
	}

	static @Nullable Integer yearFromFolder(String folderName) {
		Matcher m = YEAR_IN_FOLDER.matcher(folderName);
		if (!m.find()) {
			return null;
		}
		Integer year = Integer.valueOf(m.group(1));
		return plausible(year) ? year : null;
	}

	private static @Nullable String artistFromPath(Path file, @Nullable Path root) {
		if (root == null) {
			return null;
		}
		Path relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
		if (relative.getNameCount() < 2) {
			return null;
		}
		String folder = relative.getName(0).toString();
		// "Unknown Artist/Unknown Album/": a folder name can be a placeholder too.
		return Placeholder.missingArtist(folder) ? null : folder;
	}

	private record NumberKey(@Nullable Path folder, @Nullable String album, @Nullable Integer disc, int number) {
	}

	private static List<Track> withoutRepeatedNumbers(List<Track> tracks) {
		Map<NumberKey, Integer> counts = new HashMap<>();
		for (Track t : tracks) {
			key(t).ifPresent(k -> counts.merge(k, 1, Integer::sum));
		}
		List<Track> result = new ArrayList<>(tracks.size());
		for (Track t : tracks) {
			var k = key(t);
			if (k.isPresent() && counts.get(k.get()) > 1) {
				TrackMetadata m = t.metadata();
				result.add(t.withMetadata(new TrackMetadata(m.artist(), m.title(), m.album(), m.albumArtist(), m.year(), null, m.discNumber(), m.externalIds())));
			} else {
				result.add(t);
			}
		}
		return result;
	}

	private static java.util.Optional<NumberKey> key(Track t) {
		Integer number = t.metadata().trackNumber();
		if (number == null) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(new NumberKey(t.provenance().source().getParent(), t.metadata().album(), t
		                                                                                                       .metadata()
		                                                                                                       .discNumber(), number));
	}
}
