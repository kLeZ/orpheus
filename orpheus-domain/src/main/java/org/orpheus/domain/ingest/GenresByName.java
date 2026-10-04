package org.orpheus.domain.ingest;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.AsciiText;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseGroupSearchPort.FoundReleaseGroup;

/**
 * Genres from a search by name (ADR-0021, revision of 2026-09-27, user decision): only for an album
 * whose folder is already ordered and consistently tagged, and only when artist and title match a
 * MusicBrainz release group exactly. Outside these cases, no genre: a search by name is ambiguous and
 * a wrong genre is worse than none.
 */
public final class GenresByName {

	/**
	 * Where to take the genres from: the release group, or the artist if the album is not unique.
	 * {@code artistCredit} is the MusicBrainz spelling of the artist, equal to that of the tag except for
	 * capitalization, spaces and typographic characters: it counts as the canonical name (ADR-0020,
	 * point 3). {@code title} is the MusicBrainz spelling of the title, which replaces that of the tracks
	 * in the folder and in the tags (user decision of 2026-09-28).
	 */
	public record Choice(Optional<String> releaseGroupId,
	                     Optional<String> artistId,
	                     String artistCredit,
	                     String title,
	                     Optional<Integer> year) {

		public Choice(Optional<String> releaseGroupId, Optional<String> artistId, String artistCredit, String title) {
			this(releaseGroupId, artistId, artistCredit, title, Optional.empty());
		}
	}

	/** Artist and title to search. */
	public record Names(String artist, String title) {
	}

	private GenresByName() {
	}

	/**
	 * Artist and title of an already ordered album: all the tracks carry in the tag the same album and
	 * the same album artist (or, with no album artist on any track, the same artist), not placeholders
	 * and not Various Artists. Values as read from the file, not completed from file or folder name.
	 */
	public static Optional<Names> orderedAlbumNames(Album album) {
		List<TrackMetadata> tags = album.tracks().stream().map(Track::originalMetadata).toList();
		if (tags.isEmpty()) {
			return Optional.empty();
		}
		String title = single(tags.stream().map(m -> Placeholder.missingAlbum(m.album()) ? null : m.album()).toList());
		boolean withAlbumArtist = tags.stream().anyMatch(m -> !Placeholder.missingAlbumArtist(m.albumArtist()));
		String artist = withAlbumArtist ? single(tags.stream()
		                                             .map(m -> Placeholder.missingAlbumArtist(m.albumArtist()) ? null : m.albumArtist())
		                                             .toList()) : single(tags.stream()
		                                                                     .map(m -> Placeholder.missingArtist(m.artist()) ? null : m.artist())
		                                                                     .toList());
		if (title == null || artist == null || DestinationPath.VARIOUS_ARTISTS.equalsIgnoreCase(artist)) {
			return Optional.empty();
		}
		return Optional.of(new Names(artist, title));
	}

	/**
	 * Among the candidates only those with an identical title and credit count (except for
	 * capitalization, spaces and typographic characters). A single one, or a single album without
	 * secondary types among them ("Burn" by Deep Purple: two EPs and an album with the same name): the
	 * genres of that release group. Several candidates of the same artist: the genres of the artist.
	 * Otherwise nothing.
	 */
	public static Optional<Choice> choose(Names names, List<FoundReleaseGroup> candidates) {
		List<FoundReleaseGroup> exact = candidates.stream()
		                                          .filter(c -> normalize(c.title()).equals(normalize(names.title())) && normalize(c.artistCredit()).equals(normalize(names.artist())))
		                                          .toList();
		if (exact.isEmpty()) {
			return Optional.empty();
		}
		List<FoundReleaseGroup> albums = exact.stream()
		                                      .filter(c -> "Album".equalsIgnoreCase(c.primaryType()) && c.secondaryTypes()
		                                                                                                 .isEmpty())
		                                      .toList();
		FoundReleaseGroup single = exact.size() == 1 ? exact.getFirst() : albums.size() == 1 ? albums.getFirst() : null;
		Optional<String> commonArtist = exact.stream()
		                                     .map(FoundReleaseGroup::artistIds)
		                                     .distinct()
		                                     .count() == 1 && exact.getFirst().artistIds().size() == 1 ? Optional
		                                                                                                         .of(exact.getFirst()
		                                                                                                                  .artistIds()
		                                                                                                                  .getFirst()) : Optional.empty();
		if (single == null && commonArtist.isEmpty()) {
			return Optional.empty();
		}
		FoundReleaseGroup reference = single != null ? single : exact.getFirst();
		return Optional.of(new Choice(Optional.ofNullable(single).map(FoundReleaseGroup::id), commonArtist, reference
		                                                                                                             .artistCredit(), reference.title(), Optional.ofNullable(single)
		                                                                                                                                                         .map(FoundReleaseGroup::year)));
	}

	/**
	 * The album title for folder and tags: the MusicBrainz spelling if it differs from the local one
	 * only in capitalization, spaces and typographic characters, otherwise the local title. A local title
	 * with something more ("Entropia (Japanese Edition)" next to "Entropia") is another edition and stays
	 * separate (user decision of 2026-09-28).
	 */
	public static String albumTitle(String localTitle, Choice choice) {
		return normalize(localTitle).equals(normalize(choice.title())) ? choice.title() : localTitle;
	}

	private static String single(List<String> values) {
		if (values.stream().anyMatch(v -> v == null)) {
			return null;
		}
		List<String> distinct = values.stream().map(String::strip).distinct().toList();
		return distinct.size() == 1 ? distinct.getFirst() : null;
	}

	// Besides capitalization and spaces: "&" as "and", the leading article and the punctuation do not
	// count ("Darkness&Hope" and "Darkness and Hope", "Forgotten Tales" and "The Forgotten Tales",
	// "Where's The Beef-" with the "?" turned into "-" in the folder name).
	public static String normalize(String s) {
		String t = Normalizer.normalize(AsciiText.fromTypographic(s), Normalizer.Form.NFC)
		                     .toLowerCase(Locale.ROOT)
		                     .replace("&", " and ");
		t = t.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
		return t.startsWith("the ") ? t.substring(4) : t;
	}
}
