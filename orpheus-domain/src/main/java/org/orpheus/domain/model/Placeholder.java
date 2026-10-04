package org.orpheus.domain.model;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Single recognizer of placeholder values in tags (corrective plan, phase 1): a ripping tool or a
 * player writes "Unknown Artist", "Artista sconosciuto", "Album sconosciuto" instead of an empty tag,
 * and treating them as real values does concrete damage. On the real run of 2026-09-26 a single
 * {@code .wma} file with {@code album_artist=Artista sconosciuto} became the artist of all 22 Linkin
 * Park tracks. The list includes the Italian forms (the reference user is Italian, DESIGN.md) and is
 * non-exhaustive by construction.
 */
public final class Placeholder {

	private static final Set<String> UNKNOWN_ARTIST = Set.of("unknown", "unknown artist", "<unknown>", "[unknown]", "artista sconosciuto", "sconosciuto");

	// As the artist of a track "Various Artists" does not say who plays; as album artist it is the
	// real information of a compilation (ADR-003) and must not be discarded.
	private static final Set<String> VARIOUS_ARTISTS = Set.of("various", "various artists", "va", "artisti vari", "autori vari");

	private static final Set<String> ALBUM = Set.of(
	        "unknown", "unknown album", "untitled", "no album", "no album name", "<unknown>", "[unknown]", "album sconosciuto", "sconosciuto", "senza titolo", "nessun album");

	private static final Set<String> TITLE = Set.of("unknown", "untitled", "<unknown>", "[unknown]", "traccia sconosciuta", "senza titolo", "brano sconosciuto");

	// Values a ripping tool writes instead of a real genre; an ID3v1 number in parentheses without a
	// name says nothing useful.
	private static final Set<String> GENRE = Set.of("other", "altro", "altro genere", "genre", "unknown", "sconosciuto", "none", "misc", "miscellaneous");

	private Placeholder() {
	}

	/** True if the value is absent, blank or a placeholder, as the artist of a track. */
	public static boolean missingArtist(@Nullable String value) {
		return missing(value, UNKNOWN_ARTIST) || missing(value, VARIOUS_ARTISTS);
	}

	/** Like {@link #missingArtist}, but "Various Artists" stays a valid album artist. */
	public static boolean missingAlbumArtist(@Nullable String value) {
		return missing(value, UNKNOWN_ARTIST);
	}

	public static boolean missingAlbum(@Nullable String value) {
		return missing(value, ALBUM);
	}

	public static boolean missingTitle(@Nullable String value) {
		return missing(value, TITLE);
	}

	public static boolean missingGenre(@Nullable String value) {
		return missing(value, GENRE) || value.strip().matches("\\(?\\d+\\)?");
	}

	private static boolean missing(@Nullable String value, Set<String> placeholders) {
		return value == null || value.isBlank() || placeholders.contains(value.strip().toLowerCase(Locale.ROOT));
	}
}
