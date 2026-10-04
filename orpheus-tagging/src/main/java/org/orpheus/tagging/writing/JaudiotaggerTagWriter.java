package org.orpheus.tagging.writing;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.asf.AsfTag;
import org.jaudiotagger.tag.asf.AsfTagTextField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTMCL;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.reference.GenreTypes;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTagField;
import org.orpheus.domain.ingest.TagField;
import org.orpheus.domain.ingest.TagsToWrite;
import org.orpheus.domain.port.TagWriterPort;

/**
 * Writes the tags of ADR-0021 with jaudiotagger, Formats section. MP3: ID3v2.4 in UTF-8 (an ID3v2.3 tag
 * is converted, a file with only ID3v1 gets an ID3v2.4 initialized from it; the ID3v1 tag, if present,
 * takes the same values). FLAC and OGG: Vorbis comments. WMA: ASF attributes. Genres: several values in
 * the same field, the most voted first in MP3s and last in Vorbis comments, because Android reads only
 * the first value of an ID3 frame and only the last Vorbis field.
 */
@ApplicationScoped
public class JaudiotaggerTagWriter implements TagWriterPort {

	static final String ORIGINAL_PREFIX = "ORPHEUS_ORIGINAL_";

	private static final Map<TagField, FieldKey> KEYS = Map.ofEntries(
	        Map.entry(TagField.ARTIST, FieldKey.ARTIST), Map.entry(TagField.TITLE, FieldKey.TITLE), Map.entry(TagField.ALBUM, FieldKey.ALBUM), Map.entry(TagField.ALBUMARTIST, FieldKey.ALBUM_ARTIST), Map.entry(TagField.DATE, FieldKey.YEAR), Map.entry(TagField.TRACKNUMBER, FieldKey.TRACK), Map.entry(TagField.DISCNUMBER, FieldKey.DISC_NO), Map.entry(TagField.GENRE, FieldKey.GENRE), Map.entry(TagField.COMPILATION, FieldKey.IS_COMPILATION), Map.entry(TagField.MUSICBRAINZ_TRACKID, FieldKey.MUSICBRAINZ_TRACK_ID), Map.entry(TagField.MUSICBRAINZ_ALBUMID, FieldKey.MUSICBRAINZ_RELEASEID), Map.entry(TagField.MUSICBRAINZ_ARTISTID, FieldKey.MUSICBRAINZ_ARTISTID), Map.entry(TagField.MUSICBRAINZ_ALBUMARTISTID, FieldKey.MUSICBRAINZ_RELEASEARTISTID), Map.entry(TagField.MUSICBRAINZ_RELEASEGROUPID, FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID), Map.entry(TagField.COMPOSER, FieldKey.COMPOSER));

	static {
		TagOptionSingleton options = TagOptionSingleton.getInstance();
		options.setID3V2Version(ID3V2Version.ID3_V24);
		options.setId3v24DefaultTextEncoding(TextEncoding.UTF_8);
		options.setId3v24UnicodeTextEncoding(TextEncoding.UTF_8);
		// The genre as text, not as an ID3v1 number "(17)": the MusicBrainz genres are not in the ID3v1
		// list and a number alone is a placeholder for Placeholder.missingGenre.
		options.setWriteMp3GenresAsText(true);
		options.setWriteMp4GenresAsText(true);
		options.setPadNumbers(false);
	}

	@Override
	public void write(Path copy, TagsToWrite tag) throws TagWriteException {
		boolean nothing = tag.values().isEmpty() && tag.originals().isEmpty();
		boolean mp3 = copy.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".mp3");
		if (nothing && !mp3) {
			return;
		}
		byte[] before;
		try {
			before = Files.readAllBytes(copy);
		} catch (IOException e) {
			throw new TagWriteException(copy, "reading of the copy failed: " + e.getMessage(), e);
		}
		try {
			AudioFile file = AudioFileIO.read(copy.toFile());
			Tag written = null;
			if (!nothing) {
				written = writableTag(file);
				boolean vorbis = written instanceof VorbisCommentTag || written instanceof FlacTag;
				for (var entry : tag.values().entrySet()) {
					if (entry.getKey() == TagField.PERFORMER) {
						writePerformer(written, entry.getValue());
						continue;
					}
					List<String> values = entry.getKey() == TagField.GENRE && vorbis ? reversed(entry.getValue()) : entry.getValue();
					if (entry.getKey() == TagField.COMPOSER) {
						// The field is not among those read at import: a different original is kept here (T24).
						List<String> composerBefore = written.getAll(FieldKey.COMPOSER)
						                                     .stream()
						                                     .filter(v -> !v.isBlank())
						                                     .toList();
						if (!composerBefore.isEmpty() && !composerBefore.equals(values) && !tag.originals()
						                                                                       .containsKey(TagField.COMPOSER)) {
							writeCustom(written, ORIGINAL_PREFIX + TagField.COMPOSER.name(), composerBefore);
						}
					}
					writeField(written, KEYS.get(entry.getKey()), values);
				}
				for (var entry : tag.originals().entrySet()) {
					writeCustom(written, ORIGINAL_PREFIX + entry.getKey().name(), entry.getValue());
				}
				if (written instanceof ID3v24Tag id3) {
					// Not file.commit(): see the comment below.
					id3.write(copy.toFile(), id3v2TagEnd(before));
				} else {
					file.commit();
				}
			}
			if (file instanceof MP3File mp3File && mp3File.hasID3v1Tag()) {
				Tag v2 = written != null ? written : mp3File.hasID3v2Tag() ? mp3File.getID3v2Tag() : null;
				if (v2 != null) {
					syncId3v1(copy, mp3File.getID3v1Tag(), v2);
				}
			}
		} catch (Exception e) {
			restore(copy, before);
			throw new TagWriteException(copy, "tag writing failed: " + e.getMessage(), e);
		}
		checkAudio(copy, before);
	}

	// ID3v1 with the same values as ID3v2, within the limits of the format (ADR-0021, revision of
	// 2026-09-27): 30 characters per field, genre only from the ID3v1 list (the first of the genres that
	// fits, otherwise undefined). An empty field in ID3v2 leaves the ID3v1 value; the comment is not touched.
	private static void syncId3v1(Path copy, ID3v1Tag read, Tag v2) throws IOException {
		String track = v2.getFirst(FieldKey.TRACK);
		boolean withTrack = !track.isBlank() && track.chars().allMatch(Character::isDigit);
		// A v1.0 has no track number: it becomes v1.1 only if the comment fits in the 28 characters of
		// v1.1, otherwise it stays v1.0 without track (no truncated comment).
		ID3v1Tag v1 = withTrack && !(read instanceof ID3v11Tag) && read.getFirstComment()
		                                                               .length() <= 28 ? new ID3v11Tag(read) : read;
		String title = latin1(v2.getFirst(FieldKey.TITLE));
		String artist = latin1(v2.getFirst(FieldKey.ARTIST));
		String album = latin1(v2.getFirst(FieldKey.ALBUM));
		String year = v2.getFirst(FieldKey.YEAR);
		if (!title.isBlank()) {
			v1.setTitle(title);
		}
		if (!artist.isBlank()) {
			v1.setArtist(artist);
		}
		if (!album.isBlank()) {
			v1.setAlbum(album);
		}
		if (year.length() >= 4 && year.substring(0, 4).chars().allMatch(Character::isDigit)) {
			v1.setYear(year.substring(0, 4));
		}
		if (v1 instanceof ID3v11Tag v11 && withTrack) {
			v11.setTrack(track);
		}
		List<String> genres = v2.getAll(FieldKey.GENRE);
		if (!genres.isEmpty()) {
			v1.setGenre(genres.stream()
			                  .filter(g -> GenreTypes.getInstanceOf().getIdForName(g) != null)
			                  .findFirst()
			                  .orElse(""));
		}
		try (RandomAccessFile raf = new RandomAccessFile(copy.toFile(), "rw")) {
			v1.write(raf);
		}
	}

	// ID3v1 is ISO-8859-1 and jaudiotagger truncates every character to one byte: "紅き契約" became junk
	// (Dragon Guardian, real run of 2026-09-27). Outside ISO-8859-1, "?" as Java does for that encoding.
	static String latin1(String text) {
		return new String(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	// Why not file.commit() for MP3s: jaudiotagger writes the ID3v2 tag up to the first MPEG frame it
	// recognizes and treats everything before it as padding. On 19 copies of the real run of 2026-09-27
	// it overwrote from 58 bytes to 49 KB (MPEG-2 frames at the start of "Beatles/ - Sugar Sugar.mp3",
	// about half a second of audio). Here the audio starts where the tag declared in the header ends, and
	// ID3v24Tag.write keeps (or moves) everything that follows. The ID3v1 tag is rewritten separately.

	// Safety net: the bytes outside the tags (between ID3v2 and ID3v1, except the null padding) must stay
	// those of before. Otherwise the copy goes back as it was and the file keeps its original tags.
	private static void checkAudio(Path copy, byte[] before) throws TagWriteException {
		if (!copy.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) {
			return;
		}
		try {
			byte[] after = Files.readAllBytes(copy);
			if (!Arrays.equals(audioContent(before), audioContent(after))) {
				restore(copy, before);
				throw new TagWriteException(copy, "writing would have altered the audio, copy restored without new tags", null);
			}
		} catch (IOException e) {
			restore(copy, before);
			throw new TagWriteException(copy, "audio check failed: " + e.getMessage(), e);
		}
	}

	private static void restore(Path copy, byte[] before) {
		try {
			Files.write(copy, before);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException("restoring the copy " + copy + " failed", e);
		}
	}

	static long id3v2TagEnd(byte[] b) {
		if (b.length < 10 || b[0] != 'I' || b[1] != 'D' || b[2] != '3') {
			return 0;
		}
		long size = ((b[6] & 0x7F) << 21) | ((b[7] & 0x7F) << 14) | ((b[8] & 0x7F) << 7) | (b[9] & 0x7F);
		boolean footer = (b[5] & 0x10) != 0;
		return Math.min(b.length, 10 + size + (footer ? 10 : 0));
	}

	static byte[] audioContent(byte[] b) {
		int start = (int) id3v2TagEnd(b);
		int end = b.length;
		if (end - 128 >= start && b[end - 128] == 'T' && b[end - 127] == 'A' && b[end - 126] == 'G') {
			end -= 128;
		}
		while (start < end && b[start] == 0) {
			start++;
		}
		return Arrays.copyOfRange(b, start, end);
	}

	private static Tag writableTag(AudioFile file) {
		if (file instanceof MP3File mp3) {
			ID3v24Tag v24;
			if (mp3.hasID3v2Tag()) {
				v24 = mp3.getID3v2TagAsv24();
			} else if (mp3.hasID3v1Tag()) {
				v24 = new ID3v24Tag(mp3.getID3v1Tag());
			} else {
				v24 = new ID3v24Tag();
			}
			mp3.setID3v2Tag(v24);
			return v24;
		}
		return file.getTagOrCreateAndSetDefault();
	}

	private static void writeField(Tag tag, FieldKey key, List<String> values) throws Exception {
		tag.deleteField(key);
		if (key == FieldKey.GENRE && !multiValue(tag)) {
			tag.setField(key, values.getFirst());
			return;
		}
		for (String value : values) {
			tag.addField(key, value);
		}
	}

	// MP4 has a single genre atom: the most voted is written.
	private static boolean multiValue(Tag tag) {
		return tag instanceof ID3v24Tag || tag instanceof VorbisCommentTag || tag instanceof FlacTag || tag instanceof AsfTag;
	}

	private static void writeCustom(Tag tag, String name, List<String> values) throws Exception {
		switch (tag) {
			case ID3v24Tag id3 -> {
				ID3v24Frame frame = new ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO);
				// ID3v2.4: several values in the same frame separated by the null byte.
				frame.setBody(new FrameBodyTXXX(TextEncoding.UTF_8, name, String.join("\0", values)));
				id3.setField(frame);
			}
			case FlacTag flac -> writeVorbis(flac.getVorbisCommentTag(), name, values);
			case VorbisCommentTag vorbis -> writeVorbis(vorbis, name, values);
			case AsfTag asf -> {
				asf.deleteField(name);
				for (String value : values) {
					asf.addField(new AsfTagTextField(name, value));
				}
			}
			default -> {
				// MP4 and others: no free field written; the original stays in the database (T26).
			}
		}
	}

	private static void writeVorbis(VorbisCommentTag tag, String name, List<String> values) throws Exception {
		tag.deleteField(name);
		for (String value : values) {
			tag.addField(new VorbisCommentTagField(name, value));
		}
	}

	// PERFORMER: standard field of the Vorbis comments; in ID3v2.4 the TMCL frame (Musician Credits List)
	// with the role "performer". jaudiotagger 3.0.1 writes PERFORMER_NAME, which players do not read.
	private static void writePerformer(Tag tag, List<String> values) throws Exception {
		switch (tag) {
			case ID3v24Tag id3 -> {
				FrameBodyTMCL body = new FrameBodyTMCL();
				body.setTextEncoding(TextEncoding.UTF_8);
				for (String value : values) {
					body.addPair("performer", value);
				}
				ID3v24Frame frame = new ID3v24Frame(ID3v24Frames.FRAME_ID_MUSICIAN_CREDITS);
				frame.setBody(body);
				id3.removeFrame(ID3v24Frames.FRAME_ID_MUSICIAN_CREDITS);
				id3.setFrame(frame);
			}
			case FlacTag flac -> writeVorbis(flac.getVorbisCommentTag(), "PERFORMER", values);
			case VorbisCommentTag vorbis -> writeVorbis(vorbis, "PERFORMER", values);
			default -> writeCustom(tag, "PERFORMER", values);
		}
	}

	private static List<String> reversed(List<String> values) {
		return new ArrayList<>(values).reversed();
	}
}
