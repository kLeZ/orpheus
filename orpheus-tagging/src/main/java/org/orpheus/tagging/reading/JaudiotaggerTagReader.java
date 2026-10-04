package org.orpheus.tagging.reading;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotReadVideoException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.TagReaderPort;
import org.orpheus.domain.port.UnreadableTrackException;

@ApplicationScoped
public class JaudiotaggerTagReader implements TagReaderPort {

	private static final int ID3V1_FIELD_LENGTH = 30;
	private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+");
	private static final Pattern ISOLATED_YEAR = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d{4})(?![\\p{L}\\p{N}])");

	@Override
	public Track read(Path file) throws UnreadableTrackException {
		AudioFile audioFile = open(file);
		Tag tag = audioFile.getTag();

		TrackMetadata metadata = new TrackMetadata(
		        textField(tag, FieldKey.ARTIST), textField(tag, FieldKey.TITLE), textField(tag, FieldKey.ALBUM), textField(tag, FieldKey.ALBUM_ARTIST), yearField(tag), intField(tag, FieldKey.TRACK), intField(tag, FieldKey.DISC_NO), ExternalIds.unknown());

		return new Track(file, metadata, new Provenance(file, ProvenanceState.INTACT)).withTagGenres(genres(tag));
	}

	private static AudioFile open(Path file) throws UnreadableTrackException {
		try {
			return AudioFileIO.read(file.toFile());
		} catch (CannotReadVideoException e) {
			throw new UnreadableTrackException(file, "The file contains a video track, it is not audio (ADR-002)", e);
		} catch (CannotReadException | TagException | ReadOnlyFileException | InvalidAudioFrameException |
		         java.io.IOException e) {
			throw new UnreadableTrackException(file, "Unreadable audio file: " + e.getMessage(), e);
		}
	}

	// An absent, blank or placeholder tag is the normal case (SPEC.md section 2): jaudiotagger sometimes
	// signals it with KeyNotFoundException or UnsupportedOperationException instead of an empty string
	// (e.g. DISC_NO on a WAV with only a RIFF INFO tag, not ID3: jaudiotagger does not support it at all
	// in that case), it is not an unreadable file.
	private static String textField(Tag tag, FieldKey key) {
		if (tag == null) {
			return null;
		}
		try {
			String value = withoutId3v1Residues(tag.getFirst(key));
			return (value == null || value.isBlank()) ? null : value.trim();
		} catch (KeyNotFoundException | UnsupportedOperationException e) {
			return null;
		}
	}

	// A year counts only if the field contains exactly one group of 4 digits not attached to letters or
	// other digits: "circa 1975" and "2001-05-10" give the year; "1982ôñ" (an encoding residue, the folder
	// said 1987) and "197" (truncated) are malformed and treated as absent, not partially recovered (real
	// run of 2026-09-26).
	private static Integer yearField(Tag tag) {
		String value = textField(tag, FieldKey.YEAR);
		if (value == null) {
			return null;
		}
		Matcher m = ISOLATED_YEAR.matcher(value);
		if (!m.find()) {
			return null;
		}
		String year = m.group(1);
		while (m.find()) {
			if (!m.group(1).equals(year)) {
				return null;
			}
		}
		return Integer.valueOf(year);
	}

	// Fixed length ID3v1 fields (30 characters) followed by spurious bytes: on the real run "Concerto For
	// Group & Orchestra\u0098{\u001e\u0001ôñ" and "Concerto For Group & Orchestraðt\u001b\u0001ôñ", the same
	// album in two different folders. A control character marks the end of the content; if it appears
	// after the thirtieth character, the original field was 30 long.
	static String withoutId3v1Residues(String value) {
		if (value == null) {
			return null;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c < 0x20 && c != '\t') || (c >= 0x7f && c <= 0x9f)) {
				return value.substring(0, Math.min(i, ID3V1_FIELD_LENGTH));
			}
		}
		return value;
	}

	// All the genre values (ID3v2.4 separated by the null byte, repeated Vorbis comments): they keep the
	// original when the genre is made uniform (ADR-0021).
	private static java.util.List<String> genres(Tag tag) {
		if (tag == null) {
			return java.util.List.of();
		}
		try {
			return tag.getAll(FieldKey.GENRE)
			          .stream()
			          .map(JaudiotaggerTagReader::withoutId3v1Residues)
			          .filter(g -> g != null && !g.isBlank())
			          .map(String::strip)
			          .toList();
		} catch (KeyNotFoundException | UnsupportedOperationException e) {
			return java.util.List.of();
		}
	}

	private static Integer intField(Tag tag, FieldKey key) {
		String value = textField(tag, key);
		if (value == null) {
			return null;
		}
		Matcher m = FIRST_NUMBER.matcher(value);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.valueOf(m.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
