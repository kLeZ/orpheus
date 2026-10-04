package org.orpheus.domain.ingest;

/**
 * The fields Orpheus writes into the tags of the copies (ADR-0021). Every other field stays intact
 * (rule T25). The names follow the MusicBrainz Picard convention.
 */
public enum TagField {
	ARTIST,
	TITLE,
	ALBUM,
	ALBUMARTIST,
	DATE,
	TRACKNUMBER,
	DISCNUMBER,
	GENRE,
	COMPILATION,
	MUSICBRAINZ_TRACKID,
	MUSICBRAINZ_ALBUMID,
	MUSICBRAINZ_ARTISTID,
	MUSICBRAINZ_ALBUMARTISTID,
	MUSICBRAINZ_RELEASEGROUPID,
	/** Who plays the song, on the tracks of a compilation (user decision of 2026-10-04). */
	PERFORMER,
	/** The authors, full names in MusicBrainz order (user decision of 2026-10-04). */
	COMPOSER
}
