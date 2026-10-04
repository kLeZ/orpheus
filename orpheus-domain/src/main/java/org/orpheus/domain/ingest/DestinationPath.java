package org.orpheus.domain.ingest;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AsciiText;
import org.orpheus.domain.model.Track;

/**
 * Computes the relative destination path of a track according to the general pattern of
 * ADR-003/SPEC.md 4.3: artist / year - album / optional disc / number - title, with the Various
 * Artists variant that includes the track artist in the file name. No I/O: only the computation of
 * the {@link Path}, relative to a library root that the import orchestrator decides elsewhere.
 */
public final class DestinationPath {

	public static final String VARIOUS_ARTISTS = "Various Artists";

	private static final Pattern INVALID_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|]");
	private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");

	private DestinationPath() {
	}

	public static Path compute(Track track, @Nullable Album album) {
		return compute(track, album, CanonicalArtistNames.empty());
	}

	/**
	 * Like {@link #compute(Track, Album)}, with the name of the artist folder brought to its canonical
	 * form when MusicBrainz knows it ({@link CanonicalArtistNames}).
	 */
	public static Path compute(Track track, @Nullable Album album, CanonicalArtistNames canonical) {
		return compute(track, album, canonical, PlaceholderNames.DEFAULTS);
	}

	/** Like {@link #compute(Track, Album, CanonicalArtistNames)}, with the configured placeholder names. */
	public static Path compute(Track track, @Nullable Album album, CanonicalArtistNames canonical, PlaceholderNames names) {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(canonical, "canonical");
		Objects.requireNonNull(names, "names");
		return album == null ? computeIsolated(track, canonical, names) : computeInAlbum(track, album, canonical, names);
	}

	/**
	 * Path of a track identified by Tier 2, taken from the release (corrective plan, phase 2; corollary 2
	 * of DESIGN.md): release artist / year - release title / [Disc N] / NN - title in the tracklist.
	 * {@code multiDisc} says whether the tracks placed on this release are on different discs (same rule
	 * as {@link #discFolder}): MusicBrainz does not say how many discs the release has, only which one
	 * the track is on. Without information on the release it falls back on {@code album} as before.
	 */
	public static Path fromRelease(Track track, Release release, boolean multiDisc, @Nullable Album album, CanonicalArtistNames canonical) {
		return fromRelease(track, release, multiDisc, album, canonical, false);
	}

	/**
	 * @param ownedCompilation true when several tracks of the source were placed together on this
	 *                         release (multi-track consensus): only then does a Various Artists
	 *                         compilation go under "Various Artists" (ADR-003). A single that appears on
	 *                         MusicBrainz only on compilations stays under its artist, with the
	 *                         compilation as album.
	 */
	public static Path fromRelease(Track track, Release release, boolean multiDisc, @Nullable Album album, CanonicalArtistNames canonical, boolean ownedCompilation) {
		return fromRelease(track, release, multiDisc, album, canonical, ownedCompilation, PlaceholderNames.DEFAULTS);
	}

	/** As above, with the configured placeholder names. */
	public static Path fromRelease(Track track, Release release, boolean multiDisc, @Nullable Album album, CanonicalArtistNames canonical, boolean ownedCompilation, PlaceholderNames names) {
		Objects.requireNonNull(track, "track");
		Objects.requireNonNull(release, "release");
		ReleaseInfo info = release.info();
		if (info == null || info.title().isBlank()) {
			// Without a release title the Tier 0/1 group stays, with the year confirmed by the release.
			Album withYear = album == null ? null : new Album(album.title(), album.albumArtist(), release.releaseDate()
			                                                                                             .year(), album.completeness(), album.tracks());
			return compute(track, withYear, canonical, names);
		}
		boolean variousCredit = info.artistCredit().isBlank() || VARIOUS_ARTISTS.equalsIgnoreCase(info.artistCredit()
		                                                                                              .strip());
		boolean variousArtists = variousCredit && ownedCompilation;
		String folderArtist = releaseFolderArtist(track, release, canonical, ownedCompilation, names);
		String yearAlbumFolder = sanitize(release.releaseDate().year() + " - " + info.title());

		String number = formatNumber(info.number());
		String title = orDefault(info.trackTitle(), orDefault(track.metadata().title(), names.unknownTrack()));
		// In a compilation the performer in the file name is the one of the tracklist, the same as the
		// ARTIST tag (ADR-0021, principle 1); without a track credit, the local one.
		String performer = info.trackArtistCredit() != null && !info.trackArtistCredit().isBlank() ? info
		                                                                                                 .trackArtistCredit() : trackOrPathArtist(track, names);
		String fileNameBody = variousArtists ? performer + " - " + title : title;
		String fileName = sanitize((number != null ? number + " - " : "") + fileNameBody) + extension(track);

		Path path = Path.of(sanitize(folderArtist), yearAlbumFolder);
		if (multiDisc && info.disc() != null) {
			path = path.resolve(names.disc() + " " + info.disc());
		}
		return path.resolve(fileName);
	}

	/*
	 * SPEC.md 4.3 only requires that a track without an album does not vanish into a generic container
	 * without a trace: it prescribes no pattern for this case (unlike the general one and the Various
	 * Artists one, both governed by ADR-003). An "Unidentified" folder under the artist, when it is known
	 * from the local tags alone, still keeps a minimal tree by artist instead of piling everything into a
	 * single undifferentiated bucket; only when the artist is missing too does it fall back on the
	 * explicit "Unknown Artist" container.
	 */
	private static Path computeIsolated(Track track, CanonicalArtistNames canonical, PlaceholderNames names) {
		String artist = canonical.canonical(trackOrPathArtist(track, names));
		String number = formatNumber(track.metadata().trackNumber());
		String title = orDefault(track.metadata().title(), names.unknownTrack());

		String fileName = sanitize((number != null ? number + " - " : "") + title) + extension(track);
		return Path.of(sanitize(artist), sanitize(names.unidentified()), fileName);
	}

	private static Path computeInAlbum(Track track, Album album, CanonicalArtistNames canonical, PlaceholderNames names) {
		boolean variousArtists = VARIOUS_ARTISTS.equalsIgnoreCase(trimOrNull(album.albumArtist()));
		String folderArtist = albumFolderArtist(track, album, canonical, names);

		String yearAlbumFolder = sanitize(formatYear(album.year(), names) + " - " + albumFolderTitle(album));
		String discFolder = discFolder(album, track, names);

		String number = formatNumber(track.metadata().trackNumber());
		String title = orDefault(track.metadata().title(), names.unknownTrack());
		String fileNameBody = variousArtists ? orDefault(track.metadata().artist(), names
		                                                                                 .unknownArtist()) + " - " + title : title;
		String fileName = sanitize((number != null ? number + " - " : "") + fileNameBody) + extension(track);

		Path path = Path.of(sanitize(folderArtist), yearAlbumFolder);
		if (discFolder != null) {
			path = path.resolve(sanitize(discFolder));
		}
		return path.resolve(fileName);
	}

	/**
	 * Folder artist for a track placed from the release (not sanitized): the same value goes into the
	 * ALBUMARTIST tag (ADR-0021, principle 1).
	 */
	public static String releaseFolderArtist(Track track, Release release, CanonicalArtistNames canonical, boolean ownedCompilation) {
		return releaseFolderArtist(track, release, canonical, ownedCompilation, PlaceholderNames.DEFAULTS);
	}

	public static String releaseFolderArtist(Track track, Release release, CanonicalArtistNames canonical, boolean ownedCompilation, PlaceholderNames names) {
		ReleaseInfo info = Objects.requireNonNull(release.info(), "info");
		boolean variousCredit = info.artistCredit().isBlank() || VARIOUS_ARTISTS.equalsIgnoreCase(info.artistCredit()
		                                                                                              .strip());
		if (variousCredit) {
			if (ownedCompilation) {
				return VARIOUS_ARTISTS;
			}
			return canonical.canonical(trackOrPathArtist(track, names));
		}
		return info.artistCredit();
	}

	/** Folder artist for a track in a Tier 0/1 album (not sanitized). */
	public static String albumFolderArtist(Track track, Album album, CanonicalArtistNames canonical) {
		return albumFolderArtist(track, album, canonical, PlaceholderNames.DEFAULTS);
	}

	public static String albumFolderArtist(Track track, Album album, CanonicalArtistNames canonical, PlaceholderNames names) {
		boolean variousArtists = VARIOUS_ARTISTS.equalsIgnoreCase(trimOrNull(album.albumArtist()));
		return variousArtists ? VARIOUS_ARTISTS : canonical.canonical(chooseAlbumArtist(album, track, names));
	}

	/** Album title as it appears in the folder, without the year repeated. */
	public static String albumFolderTitle(Album album) {
		return withoutRepeatedYear(album.title(), album.year());
	}

	/** Artist used for an isolated track or as a fallback: tag, then top-level folder. */
	public static String canonicalTrackOrPathArtist(Track track, CanonicalArtistNames canonical) {
		return canonicalTrackOrPathArtist(track, canonical, PlaceholderNames.DEFAULTS);
	}

	public static String canonicalTrackOrPathArtist(Track track, CanonicalArtistNames canonical, PlaceholderNames names) {
		return canonical.canonical(trackOrPathArtist(track, names));
	}

	private static String chooseAlbumArtist(Album album, Track track, PlaceholderNames names) {
		String albumArtist = trimOrNull(album.albumArtist());
		if (albumArtist != null && !albumArtist.isEmpty()) {
			return albumArtist;
		}
		// If the album title is the name of the same folder the artist would come from, that folder is the
		// album, not the artist ("Nouveau titre/01 - Plage 1.mp3" became artist "Nouveau titre", album
		// "Nouveau titre").
		if (track.artistFromPath() != null && track.artistFromPath().strip().equalsIgnoreCase(album.title().strip())) {
			return orDefault(track.metadata().artist(), names.unknownArtist());
		}
		return trackOrPathArtist(track, names);
	}

	// The name of the top-level folder of the source is a better fallback than "Unknown Artist" when no
	// tag says who plays (Skunk Anansie, files downloaded without tags).
	private static String trackOrPathArtist(Track track, PlaceholderNames names) {
		return orDefault(track.metadata().artist(), orDefault(track.artistFromPath(), names.unknownArtist()));
	}

	/*
	 * A missing year is the typical case of AlbumCompleteness.UNVERIFIED (Tier 0/1, no external
	 * source consulted): an invented year would be false data passed off as true. A text placeholder,
	 * not a number, keeps the "X - album" pattern intact and stays immediately recognizable as "to be
	 * checked" when browsing the folders, instead of silently disappearing behind a plausible but
	 * unverified year.
	 */
	private static String formatYear(@Nullable Integer year, PlaceholderNames names) {
		return year != null ? String.valueOf(year) : names.unknownYear();
	}

	/*
	 * "Multi-disc" is derived from the distinct disc numbers *present in this Album*, not from a
	 * comparison with an official tracklist: if the user owns a single isolated track of disc 2 of a box
	 * set, that track arrives here already as an album of its own with one track (this is not the method
	 * that decides the grouping) and so has a single distinct disc: no "Disc 2" folder, the number still
	 * stays in the tags. It is an accepted consequence, not a bug: the alternative (a disc folder for
	 * every disc number > 1 regardless of the other discs present) creates asymmetry on complete albums
	 * instead (disc 1 tracks loose next to a "Disc 2" folder).
	 */
	private static @Nullable String discFolder(Album album, Track track, PlaceholderNames names) {
		Set<Integer> distinctDiscs = new LinkedHashSet<>();
		for (Track t : album.tracks()) {
			Integer discNumber = t.metadata().discNumber();
			if (discNumber != null) {
				distinctDiscs.add(discNumber);
			}
		}
		if (distinctDiscs.size() < 2) {
			return null;
		}
		Integer trackDisc = track.metadata().discNumber();
		return trackDisc != null ? names.disc() + " " + trackDisc : null;
	}

	private static @Nullable String formatNumber(@Nullable Integer trackNumber) {
		return trackNumber != null ? String.format("%02d", trackNumber) : null;
	}

	private static String extension(Track track) {
		String fileName = track.physicalPath().getFileName().toString();
		int lastDot = fileName.lastIndexOf('.');
		return lastDot >= 0 ? fileName.substring(lastDot) : "";
	}

	private static @Nullable String trimOrNull(@Nullable String value) {
		return value == null ? null : value.strip();
	}

	private static String orDefault(@Nullable String value, String fallback) {
		String clean = trimOrNull(value);
		return clean == null || clean.isEmpty() ? fallback : clean;
	}

	// Typographic characters to ASCII (AsciiText): MusicBrainz writes "blink‐182" (U+2010) and "Don’t",
	// and folders differing only in that character look identical.
	private static String sanitize(String segment) {
		String ascii = AsciiText.fromTypographic(segment);
		return MULTIPLE_SPACES.matcher(INVALID_CHARACTERS.matcher(ascii).replaceAll("-")).replaceAll(" ").strip();
	}

	// "Waiting For The Sun (1968)" in the "1968 - ..." folder would repeat the year.
	private static String withoutRepeatedYear(String title, @Nullable Integer year) {
		if (year == null) {
			return title;
		}
		String without = title.replaceAll("\\s*[(\\[]" + year + "[)\\]]\\s*$", "")
		                      .replaceAll("^[(\\[]" + year + "[)\\]]\\s*", "")
		                      .strip();
		return without.isEmpty() ? title : without;
	}
}
