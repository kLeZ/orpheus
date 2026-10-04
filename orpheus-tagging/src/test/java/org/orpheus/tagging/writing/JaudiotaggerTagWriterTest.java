package org.orpheus.tagging.writing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.orpheus.domain.ingest.TagField;
import org.orpheus.domain.ingest.TagsToWrite;

/**
 * Real writing on the silence fixtures of {@code src/test/resources/audio}, read back with jaudiotagger.
 * The check on real user files (covers, comments, FMPS, conversion from ID3v2.3 and from ID3v1 only,
 * decoded audio unchanged) is documented in ADR-0021.
 */
class JaudiotaggerTagWriterTest {

	private static final List<String> GENERI = List.of("Hard Rock", "Rock", "Glam Metal");

	private final JaudiotaggerTagWriter sut = new JaudiotaggerTagWriter();

	private static TagsToWrite tag() {
		return new TagsToWrite(Map.of(TagField.ARTIST, List.of("The Archies"), TagField.TITLE, List.of("Sugar, Sugar"), TagField.TRACKNUMBER, List.of("7"), TagField.GENRE, GENERI, TagField.MUSICBRAINZ_TRACKID, List.of("rec-1"), TagField.MUSICBRAINZ_RELEASEGROUPID, List.of("rg-1")), Map.of(TagField.ARTIST, List.of("Beatles")), List.of());
	}

	@ParameterizedTest
	@ValueSource(strings = {
	                        "mp3",
	                        "flac",
	                        "ogg",
	                        "wma"
	})
	void writesTheFieldsAndKeepsTheOriginal(String extension, @TempDir Path dir) throws Exception {
		Path file = copyFixture(extension, dir);
		preexisting(file, FieldKey.COMMENT, "comment to keep");

		sut.write(file, tag());

		Tag read = AudioFileIO.read(file.toFile()).getTag();
		assertThat(read.getFirst(FieldKey.ARTIST)).isEqualTo("The Archies");
		assertThat(read.getFirst(FieldKey.TITLE)).isEqualTo("Sugar, Sugar");
		assertThat(read.getFirst(FieldKey.TRACK)).isEqualTo("7");
		assertThat(read.getFirst(FieldKey.MUSICBRAINZ_TRACK_ID)).isEqualTo("rec-1");
		assertThat(read.getFirst(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID)).isEqualTo("rg-1");
		assertThat(read.getFirst(FieldKey.COMMENT)).isEqualTo("comment to keep");
		assertThat(original(read, "ARTIST")).containsExactly("Beatles");
	}

	// Android reads only the first value of the ID3 frame and only the last Vorbis field (ADR-0021).
	@Test
	void mp3WithTheMostVotedGenreFirstInASingleId3v24Frame(@TempDir Path dir) throws Exception {
		Path file = copyFixture("mp3", dir);

		sut.write(file, tag());

		MP3File mp3 = (MP3File) AudioFileIO.read(file.toFile());
		assertThat(mp3.getID3v2Tag()).isInstanceOf(ID3v24Tag.class);
		assertThat(mp3.getID3v2Tag().getAll(FieldKey.GENRE)).containsExactlyElementsOf(GENERI);
		assertThat(mp3.getID3v2Tag().getFrame("TCON")).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {
	                        "flac",
	                        "ogg"
	})
	void vorbisWithTheMostVotedGenreLast(String extension, @TempDir Path dir) throws Exception {
		Path file = copyFixture(extension, dir);

		sut.write(file, tag());

		assertThat(AudioFileIO.read(file.toFile()).getTag().getAll(FieldKey.GENRE))
		                                                                           .containsExactly("Glam Metal", "Rock", "Hard Rock");
	}

	@Test
	void anId3v23IsConvertedWithoutLosingTheUntouchedFields(@TempDir Path dir) throws Exception {
		Path file = copyFixture("mp3", dir);
		MP3File mp3 = (MP3File) AudioFileIO.read(file.toFile());
		ID3v23Tag v23 = new ID3v23Tag();
		v23.setField(FieldKey.ALBUM, "Everything's Archie");
		v23.setField(FieldKey.TRACK_TOTAL, "10");
		v23.setField(FieldKey.TRACK, "3");
		mp3.setID3v2Tag(v23);
		mp3.commit();

		sut.write(file, tag());

		Tag read = AudioFileIO.read(file.toFile()).getTag();
		assertThat(read).isInstanceOf(ID3v24Tag.class);
		assertThat(read.getFirst(FieldKey.ALBUM)).isEqualTo("Everything's Archie");
		assertThat(read.getFirst(FieldKey.TRACK)).isEqualTo("7");
		assertThat(read.getFirst(FieldKey.TRACK_TOTAL)).isEqualTo("10");
	}

	// Non-null bytes between the declared tag and the first frame jaudiotagger recognizes (MPEG-2 frames
	// in "Beatles/ - Sugar Sugar.mp3"): jaudiotagger treated them as padding and wrote the tag over them.
	@Test
	void theBytesAfterTheDeclaredTagAreNotOverwritten(@TempDir Path dir) throws Exception {
		Path file = copyFixture("mp3", dir);
		byte[] audio = Files.readAllBytes(file);
		byte[] spurious = new byte[4000];
		for (int i = 0; i < spurious.length; i++) {
			spurious[i] = (byte) (i % 7 == 0 ? 0x55 : 0x33);
		}
		byte[] tag = {
		              'I',
		              'D',
		              '3',
		              4,
		              0,
		              0,
		              0,
		              0,
		              0,
		              0
		};
		Files.write(file, concatenate(tag, spurious, JaudiotaggerTagWriter.audioContent(audio)));
		byte[] before = JaudiotaggerTagWriter.audioContent(Files.readAllBytes(file));

		sut.write(file, tag());

		assertThat(JaudiotaggerTagWriter.audioContent(Files.readAllBytes(file))).isEqualTo(before);
		assertThat(AudioFileIO.read(file.toFile()).getTag().getFirst(FieldKey.ARTIST)).isEqualTo("The Archies");
	}

	private static byte[] concatenate(byte[]... parts) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.writeBytes(p);
		}
		return out.toByteArray();
	}

	// ADR-0021, revision of 2026-09-27: ID3v1 with the same values as ID3v2, genre from the ID3v1 list.
	@Test
	void theId3v1TakesTheWrittenValues(@TempDir Path dir) throws Exception {
		Path file = copyFixture("mp3", dir);
		MP3File mp3 = (MP3File) AudioFileIO.read(file.toFile());
		ID3v11Tag v1 = new ID3v11Tag();
		v1.setArtist("Beatles");
		v1.setTitle("Sugar Sugar");
		v1.setComment("comment to keep");
		v1.setGenre("Jazz+Funk");
		mp3.setID3v1Tag(v1);
		mp3.commit();
		byte[] audio = JaudiotaggerTagWriter.audioContent(Files.readAllBytes(file));

		sut.write(file, tag());

		ID3v1Tag read = ((MP3File) AudioFileIO.read(file.toFile())).getID3v1Tag();
		assertThat(read.getFirst(FieldKey.ARTIST)).isEqualTo("The Archies");
		assertThat(read.getFirst(FieldKey.TITLE)).isEqualTo("Sugar, Sugar");
		assertThat(read.getFirst(FieldKey.TRACK)).isEqualTo("7");
		assertThat(read.getFirst(FieldKey.GENRE)).isEqualTo("Hard Rock");
		assertThat(read.getFirst(FieldKey.COMMENT)).isEqualTo("comment to keep");
		assertThat(JaudiotaggerTagWriter.audioContent(Files.readAllBytes(file))).isEqualTo(audio);
	}

	@Test
	void inTheId3v1CharactersOutsideIso88591BecomeQuestionMarks() {
		assertThat(JaudiotaggerTagWriter.latin1("Shinwa [\u795E\u8A71] \u00E8")).isEqualTo("Shinwa [??] \u00E8");
	}

	@Test
	void withNothingToWriteTheId3v1AlignsWithTheExistingId3v2WithoutTouchingIt(@TempDir Path dir) throws Exception {
		Path file = copyFixture("mp3", dir);
		MP3File mp3 = (MP3File) AudioFileIO.read(file.toFile());
		ID3v24Tag v2 = new ID3v24Tag();
		v2.setField(FieldKey.ARTIST, "The Archies");
		mp3.setID3v2Tag(v2);
		ID3v11Tag v1 = new ID3v11Tag();
		v1.setArtist("Beatles");
		mp3.setID3v1Tag(v1);
		mp3.commit();
		byte[] before = Files.readAllBytes(file);

		sut.write(file, new TagsToWrite(Map.of(), Map.of(), List.of()));

		byte[] after = Files.readAllBytes(file);
		assertThat(((MP3File) AudioFileIO.read(file.toFile())).getID3v1Tag().getFirst(FieldKey.ARTIST))
		                                                                                               .isEqualTo("The Archies");
		assertThat(java.util.Arrays.copyOf(after, after.length - 128)).isEqualTo(java.util.Arrays.copyOf(before, before.length - 128));
	}

	@Test
	void withNothingToWriteTheFileIsNotTouched(@TempDir Path dir) throws Exception {
		Path file = copyFixture("flac", dir);
		byte[] before = Files.readAllBytes(file);

		sut.write(file, new TagsToWrite(Map.of(), Map.of(), List.of()));

		assertThat(Files.readAllBytes(file)).isEqualTo(before);
	}

	private static List<String> original(Tag tag, String field) {
		String name = JaudiotaggerTagWriter.ORIGINAL_PREFIX + field;
		if (tag instanceof ID3v24Tag id3) {
			return id3.getFrame("TXXX")
			          .stream()
			          .map(f -> (FrameBodyTXXX) ((AbstractID3v2Frame) f).getBody())
			          .filter(b -> b.getDescription().equals(name))
			          .flatMap(b -> b.getValues().stream())
			          .toList();
		}
		return tag.getFields(name).stream().map(f -> ((TagTextField) f).getContent()).toList();
	}

	private static void preexisting(Path file, FieldKey key, String value) throws Exception {
		AudioFile af = AudioFileIO.read(file.toFile());
		af.getTagOrCreateAndSetDefault().setField(key, value);
		af.commit();
	}

	private static Path copyFixture(String extension, Path dir) throws Exception {
		Path destination = dir.resolve("track." + extension);
		try (InputStream in = JaudiotaggerTagWriterTest.class.getResourceAsStream("/audio/minimal." + extension)) {
			Files.copy(in, destination);
		}
		return destination;
	}

	// Standard PERFORMER: TMCL frame in ID3v2.4, PERFORMER field in the Vorbis comments.
	@ParameterizedTest
	@ValueSource(strings = {
	                        "mp3",
	                        "flac"
	})
	void performerAndComposer(String extension, @TempDir Path dir) throws Exception {
		Path file = copyFixture(extension, dir);
		preexisting(file, FieldKey.COMPOSER, "Author in the file");

		sut.write(file, new TagsToWrite(Map.of(TagField.PERFORMER, List.of("Metallica"), TagField.COMPOSER, List.of("Jon Lord", "Ian Paice")), Map.of(), List.of()));

		Tag read = AudioFileIO.read(file.toFile()).getTag();
		assertThat(read.getAll(FieldKey.COMPOSER)).containsExactly("Jon Lord", "Ian Paice");
		assertThat(original(read, "COMPOSER")).containsExactly("Author in the file");
		if (read instanceof ID3v24Tag id3) {
			Object found = id3.getFrame("TMCL");
			List<?> frame = found instanceof List<?> list ? list : List.of(found);
			assertThat(frame).hasSize(1);
			org.jaudiotagger.tag.id3.framebody.FrameBodyTMCL tmcl = (org.jaudiotagger.tag.id3.framebody.FrameBodyTMCL) ((org.jaudiotagger.tag.id3.AbstractID3v2Frame) frame.getFirst()).getBody();
			assertThat(tmcl.getKeyAtIndex(0)).isEqualTo("performer");
			assertThat(tmcl.getValueAtIndex(0)).isEqualTo("Metallica");
		} else {
			assertThat(((org.jaudiotagger.tag.flac.FlacTag) read).getVorbisCommentTag().getAll("PERFORMER"))
			                                                                                                .containsExactly("Metallica");
		}
	}
}
