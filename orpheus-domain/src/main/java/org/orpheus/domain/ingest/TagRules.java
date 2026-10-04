package org.orpheus.domain.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.AsciiText;
import org.orpheus.domain.model.Track;

/**
 * The tag writing rules of ADR-0021, one per row of the table (the identifiers T01... and G01... in the
 * comments). {@code WRITE}: the field takes the value even if the original tag is valid and different,
 * and the original is kept (T24) and recorded as a correction (T26). {@code FILL}: the field takes the
 * value only if the original tag is absent, a placeholder or malformed. The original tag is the one
 * read from the file ({@link Track#originalMetadata()}), not the value completed from file or folder
 * name: a title taken from the file name never replaces a real tag.
 *
 * <p>Revision of 2026-09-27 (user decisions): every value that appears in the path is written over a
 * valid tag too, so that tags and tree always say the same thing (T07, T10, T12 and the canonical
 * artist); every text written, and every text of the file with typographic characters, is turned into
 * ASCII ({@link AsciiText}).
 */
public final class TagRules {

	static final int MAX_GENRES = 5;
	private static final String VARIOUS_ARTISTS = "Various Artists";

	private TagRules() {
	}

	public static TagsToWrite apply(TagContext c) {
		Result r = new Result(c.track());
		Release release = c.release().orElse(null);
		ReleaseInfo info = release == null ? null : release.info();
		TrackMetadata current = c.track().metadata();

		if (info != null) {
			// T01: credit of the track in the tracklist; for a compilation without a track credit, the
			// artist of the track itself.
			String artist = info.trackArtistCredit() != null ? info.trackArtistCredit() : variousCredit(info) ? null : info.artistCredit();
			r.write(TagField.ARTIST, artist);
			r.write(TagField.TITLE, info.trackTitle()); // T04
			r.write(TagField.ALBUM, info.title()); // T06
			r.write(TagField.ALBUMARTIST, c.ownedCompilation() ? VARIOUS_ARTISTS : c.folderArtist()); // T08, T09
			r.write(TagField.DATE, String.valueOf(release.releaseDate().year())); // T11
			r.write(TagField.TRACKNUMBER, text(info.number())); // T13
			if (c.multiDisc()) {
				r.write(TagField.DISCNUMBER, text(info.disc())); // T15
			}
			if (c.ownedCompilation()) {
				r.write(TagField.COMPILATION, "1"); // T17
				// Compilation: who plays the song also as performer (user decision of 2026-10-04).
				r.write(TagField.PERFORMER, artist);
			}
			r.write(TagField.MUSICBRAINZ_TRACKID, info.recordingId()); // T19
			r.write(TagField.MUSICBRAINZ_ALBUMID, release.releaseId()); // T20
			r.writeList(TagField.MUSICBRAINZ_ARTISTID, info.trackArtistIds()); // T21
			r.writeList(TagField.MUSICBRAINZ_ALBUMARTISTID, info.releaseArtistIds()); // T22
			r.write(TagField.MUSICBRAINZ_RELEASEGROUPID, info.releaseGroupId()); // T23
		} else {
			// T02: the tag artist stays, in the canonical form used for the folder ("ACDC" becomes "AC/DC",
			// "Beatles" under "The Beatles/"). T03: without one, the top-level folder, unless it is the
			// album title itself (as for the path).
			if (!r.original(TagField.ARTIST).isEmpty()) {
				r.write(TagField.ARTIST, c.canonicalTrackArtist());
			}
			String parent = c.track().artistFromPath();
			if (parent != null && (c.albumTitle() == null || !parent.strip()
			                                                        .equalsIgnoreCase(c.albumTitle().strip()))) {
				r.fill(TagField.ARTIST, c.folderArtist() != null && c.albumTitle() == null ? c.folderArtist() : parent);
			}
			r.fill(TagField.TITLE, current.title()); // T05
			if (c.albumTitle() != null) {
				r.write(TagField.ALBUM, c.albumTitle()); // T07
				r.write(TagField.ALBUMARTIST, c.folderArtist()); // T10
				r.write(TagField.DATE, text(c.albumYear())); // T12
				if (VARIOUS_ARTISTS.equals(c.folderArtist())) {
					// Compilation recognized from the tags (ADR-0024): who plays the song also as performer.
					r.write(TagField.COMPILATION, "1");
					r.write(TagField.PERFORMER, c.canonicalTrackArtist() != null ? c.canonicalTrackArtist() : current.artist());
				}
			}
			r.fill(TagField.TRACKNUMBER, text(current.trackNumber())); // T14
			// The disc derived from the folder ("CD2") is needed for the "Disc N" subfolder: into the tag if
			// missing.
			r.fill(TagField.DISCNUMBER, text(current.discNumber()));
		}
		// Authors according to MusicBrainz, on every track whose recording is known (user decision of
		// 2026-10-04); without known authors the field of the file stays.
		r.writeList(TagField.COMPOSER, c.composers());
		// G01, G02: genres from the most voted, made uniform even if the file already has some; G03: no
		// MusicBrainz genre, the genre of the file stays.
		if (!c.genres().isEmpty()) {
			r.writeList(TagField.GENRE, c.genres().stream().limit(MAX_GENRES).map(TagRules::formatGenre).toList());
		}
		// Texts of the file not touched by the rules but with typographic characters: into ASCII too.
		for (TagField field : List.of(TagField.ARTIST, TagField.TITLE, TagField.ALBUM, TagField.ALBUMARTIST, TagField.GENRE)) {
			if (!r.values.containsKey(field)) {
				List<String> before = r.original(field);
				List<String> ascii = before.stream().map(AsciiText::fromTypographic).toList();
				if (!ascii.equals(before)) {
					r.writeList(field, ascii);
				}
			}
		}
		return new TagsToWrite(r.values, r.originals, r.corrections);
	}

	/** "hard rock" becomes "Hard Rock"; initials after a space, a dash or a slash become uppercase. */
	static String formatGenre(String genre) {
		StringBuilder s = new StringBuilder(genre.length());
		boolean start = true;
		for (char ch : genre.strip().toCharArray()) {
			s.append(start ? Character.toUpperCase(ch) : ch);
			start = ch == ' ' || ch == '-' || ch == '/';
		}
		return s.toString();
	}

	private static boolean variousCredit(ReleaseInfo info) {
		return info.artistCredit().isBlank() || VARIOUS_ARTISTS.equalsIgnoreCase(info.artistCredit().strip());
	}

	private static @Nullable String text(@Nullable Integer n) {
		return n == null || n <= 0 ? null : String.valueOf(n);
	}

	private static final class Result {

		private final Map<TagField, List<String>> values = new LinkedHashMap<>();
		private final Map<TagField, List<String>> originals = new LinkedHashMap<>();
		private final List<TagsToWrite.Correction> corrections = new ArrayList<>();
		private final Track track;

		Result(Track track) {
			this.track = track;
		}

		void write(TagField field, @Nullable String value) {
			writeList(field, value == null || value.isBlank() ? List.of() : List.of(value));
		}

		void writeList(TagField field, List<String> newValues) {
			if (newValues.isEmpty()) {
				return;
			}
			List<String> written = newValues.stream().map(AsciiText::fromTypographic).toList();
			List<String> before = original(field);
			values.put(field, written);
			if (!before.isEmpty() && !same(before, written)) {
				originals.put(field, before);
				corrections.add(new TagsToWrite.Correction(field, before, written));
			}
		}

		void fill(TagField field, @Nullable String value) {
			if (value != null && !value.isBlank() && original(field).isEmpty()) {
				values.put(field, List.of(AsciiText.fromTypographic(value)));
			}
		}

		// The valid original value of the field, read from the file: empty if absent or a placeholder.
		List<String> original(TagField field) {
			TrackMetadata m = track.originalMetadata();
			return switch (field) {
				case ARTIST -> Placeholder.missingArtist(m.artist()) ? List.of() : List.of(m.artist());
				case TITLE -> Placeholder.missingTitle(m.title()) ? List.of() : List.of(m.title());
				case ALBUM -> Placeholder.missingAlbum(m.album()) ? List.of() : List.of(m.album());
				case ALBUMARTIST -> Placeholder.missingAlbumArtist(m.albumArtist()) ? List.of() : List.of(m
				                                                                                           .albumArtist());
				case DATE -> m.year() == null ? List.of() : List.of(String.valueOf(m.year()));
				case TRACKNUMBER -> m.trackNumber() == null || m.trackNumber() <= 0 ? List.of() : List.of(String
				                                                                                                .valueOf(m.trackNumber()));
				case DISCNUMBER -> m.discNumber() == null || m.discNumber() <= 0 ? List.of() : List.of(String.valueOf(m
				                                                                                                       .discNumber()));
				case GENRE -> track.tagGenres().stream().filter(g -> !Placeholder.missingGenre(g)).toList();
				default -> List.of();
			};
		}

		// Exact equality, except for spaces at the edges: "In The End" against "In the End" is a
		// correction.
		private static boolean same(List<String> a, List<String> b) {
			if (a.size() != b.size()) {
				return false;
			}
			for (int i = 0; i < a.size(); i++) {
				if (!a.get(i).strip().equals(b.get(i).strip())) {
					return false;
				}
			}
			return true;
		}
	}

}
