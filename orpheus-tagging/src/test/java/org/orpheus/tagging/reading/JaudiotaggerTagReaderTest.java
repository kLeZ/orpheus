package org.orpheus.tagging.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.UnreadableTrackException;

/**
 * Real round trip: the fixtures in {@code src/test/resources/audio} are minimal silence files generated
 * with ffmpeg (not user files), on which these tests write the tags with the writing API of jaudiotagger
 * itself and then read them back with {@link JaudiotaggerTagReader}.
 */
class JaudiotaggerTagReaderTest {

	private final JaudiotaggerTagReader sut = new JaudiotaggerTagReader();

	@Test
	void roundTripMp3(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.mp3", tempDir.resolve("track.mp3"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	@Test
	void roundTripFlac(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.flac", tempDir.resolve("track.flac"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	@Test
	void roundTripOgg(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.ogg", tempDir.resolve("track.ogg"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	@Test
	void roundTripM4a(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.m4a", tempDir.resolve("track.m4a"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	// WMA is the format ADR-002 marks as the least mature in jaudiotagger: checked here concretely with the
	// same rigor as the others, not assumed from the documentation.
	@Test
	void roundTripWma(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.wma", tempDir.resolve("track.wma"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	// A "bare" WAV (no pre-existing RIFF INFO chunk) makes jaudiotagger choose an active ID3 tag by
	// default: full round trip, disc number included.
	@Test
	void roundTripWavWithId3Tag(@TempDir Path tempDir) throws Exception {
		Path file = createMinimalWav(tempDir.resolve("track.wav"));
		writeTag(file, "Test Artist", "Test Title", "Test Album", "Test Album Artist", "2007", "3", "1");

		checkRoundTrip(file);
	}

	// A real limit checked concretely (not from the documentation): when the active tag of a WAV is the
	// RIFF INFO one (common for files tagged by tools other than those writing ID3, e.g. many encoders)
	// jaudiotagger does not support the disc number at all for that tag: it throws
	// UnsupportedOperationException both when reading and when writing. The port must treat it as an
	// absent field (null), not as an unreadable file.
	@Test
	void aWavWithOnlyAnInfoTagDoesNotSupportTheDiscNumber(@TempDir Path tempDir) throws Exception {
		WavOptions originalOptions = TagOptionSingleton.getInstance().getWavOptions();
		TagOptionSingleton.getInstance().setWavOptions(WavOptions.READ_INFO_ONLY);
		try {
			Path file = createMinimalWav(tempDir.resolve("track-info.wav"));
			AudioFile audioFile = AudioFileIO.read(file.toFile());
			Tag tag = audioFile.getTagOrCreateAndSetDefault();
			tag.setField(FieldKey.ARTIST, "Test Artist");
			tag.setField(FieldKey.TITLE, "Test Title");
			tag.setField(FieldKey.ALBUM, "Test Album");
			tag.setField(FieldKey.ALBUM_ARTIST, "Test Album Artist");
			tag.setField(FieldKey.YEAR, "2007");
			tag.setField(FieldKey.TRACK, "3");
			assertThatThrownBy(() -> tag.setField(FieldKey.DISC_NO, "1"))
			                                                             .isInstanceOf(UnsupportedOperationException.class);
			AudioFileIO.write(audioFile);

			Track track = sut.read(file);

			assertThat(track.metadata().artist()).isEqualTo("Test Artist");
			assertThat(track.metadata().title()).isEqualTo("Test Title");
			assertThat(track.metadata().album()).isEqualTo("Test Album");
			assertThat(track.metadata().albumArtist()).isEqualTo("Test Album Artist");
			assertThat(track.metadata().year()).isEqualTo(2007);
			assertThat(track.metadata().trackNumber()).isEqualTo(3);
			assertThat(track.metadata().discNumber()).isNull();
		} finally {
			TagOptionSingleton.getInstance().setWavOptions(originalOptions);
		}
	}

	// ADR-002: the discriminator is the actual presence of a video track, not the extension. jaudiotagger
	// itself already implements this check (Mp4InfoReader looks for a VMHD box) through
	// CannotReadVideoException; .m4a shares the same reader as the .mp4 used here.
	@Test
	void anMp4FileWithAVideoTrackIsReportedAsUnreadable(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal-video.mp4", tempDir.resolve("clip.mp4"));

		assertThatThrownBy(() -> sut.read(file)).isInstanceOf(UnreadableTrackException.class);
	}

	@Test
	void placeholderTagsAndEmptyFieldsProduceAValidTrackWithoutExceptions(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.mp3", tempDir.resolve("track-partial.mp3"));
		AudioFile audioFile = AudioFileIO.read(file.toFile());
		Tag tag = audioFile.getTagOrCreateAndSetDefault();
		tag.setField(FieldKey.TITLE, "Track 1");
		tag.setField(FieldKey.ARTIST, "");
		AudioFileIO.write(audioFile);

		Track track = sut.read(file);

		assertThat(track.metadata().title()).isEqualTo("Track 1");
		assertThat(track.metadata().artist()).isNull();
		assertThat(track.metadata().album()).isNull();
		assertThat(track.metadata().albumArtist()).isNull();
		assertThat(track.metadata().year()).isNull();
		assertThat(track.metadata().trackNumber()).isNull();
		assertThat(track.metadata().discNumber()).isNull();
	}

	@Test
	void aFileWithoutAnyTagProducesAllNullMetadata(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.flac", tempDir.resolve("no-tag.flac"));

		Track track = sut.read(file);

		assertThat(track.metadata().artist()).isNull();
		assertThat(track.metadata().title()).isNull();
		assertThat(track.metadata().album()).isNull();
		assertThat(track.metadata().albumArtist()).isNull();
		assertThat(track.metadata().year()).isNull();
		assertThat(track.metadata().trackNumber()).isNull();
		assertThat(track.metadata().discNumber()).isNull();
		assertThat(track.provenance()).isEqualTo(new Provenance(file, ProvenanceState.INTACT));
	}

	// The "number/total" format (e.g. "3/12") is a Vorbis Comment level convention (free text):
	// jaudiotagger accepts it as is for FLAC/OGG. On MP3/ID3 instead setField(TRACK,...) expects only the
	// number (the total has a separate FieldKey) and silently drops a value with "/", which is why the
	// fixture here is FLAC, not MP3.
	@Test
	void trackAndDiscNumbersInNTotalFormatAreReducedToTheFirstNumber(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.flac", tempDir.resolve("track-ntot.flac"));
		AudioFile audioFile = AudioFileIO.read(file.toFile());
		Tag tag = audioFile.getTagOrCreateAndSetDefault();
		tag.setField(FieldKey.TRACK, "3/12");
		tag.setField(FieldKey.DISC_NO, "1/2");
		AudioFileIO.write(audioFile);

		Track track = sut.read(file);

		assertThat(track.metadata().trackNumber()).isEqualTo(3);
		assertThat(track.metadata().discNumber()).isEqualTo(1);
	}

	@Test
	void aYearWithAttachedResiduesOrTruncatedIsTreatedAsAbsent(@TempDir Path tempDir) throws Exception {
		for (String value : java.util.List.of("1982\u00f4\u00f1", "197", "1975/1976")) {
			Path file = copyFixture("/audio/minimal.flac", tempDir.resolve("year-" + value.length() + ".flac"));
			AudioFile audioFile = AudioFileIO.read(file.toFile());
			audioFile.getTagOrCreateAndSetDefault().setField(FieldKey.YEAR, value);
			AudioFileIO.write(audioFile);

			assertThat(sut.read(file).metadata().year()).as(value).isNull();
		}
	}

	@Test
	void aCorruptedYearWithSurroundingTextIsStillExtracted(@TempDir Path tempDir) throws Exception {
		Path file = copyFixture("/audio/minimal.flac", tempDir.resolve("corrupted-year.flac"));
		AudioFile audioFile = AudioFileIO.read(file.toFile());
		Tag tag = audioFile.getTagOrCreateAndSetDefault();
		tag.setField(FieldKey.YEAR, "circa 1975");
		AudioFileIO.write(audioFile);

		Track track = sut.read(file);

		assertThat(track.metadata().year()).isEqualTo(1975);
	}

	@Test
	void aFileWithAudioExtensionButCorruptedContentThrows(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("corrupted.mp3");
		byte[] junk = new byte[512];
		new Random(42).nextBytes(junk);
		Files.write(file, junk);

		assertThatThrownBy(() -> sut.read(file)).isInstanceOf(UnreadableTrackException.class);
	}

	@Test
	void anExtensionOutsideTheSixSupportedThrows(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("notes.txt");
		Files.writeString(file, "not an audio file");

		assertThatThrownBy(() -> sut.read(file)).isInstanceOf(UnreadableTrackException.class);
	}

	private void checkRoundTrip(Path file) throws Exception {
		Track track = sut.read(file);

		assertThat(track.metadata().artist()).isEqualTo("Test Artist");
		assertThat(track.metadata().title()).isEqualTo("Test Title");
		assertThat(track.metadata().album()).isEqualTo("Test Album");
		assertThat(track.metadata().albumArtist()).isEqualTo("Test Album Artist");
		assertThat(track.metadata().year()).isEqualTo(2007);
		assertThat(track.metadata().trackNumber()).isEqualTo(3);
		assertThat(track.metadata().discNumber()).isEqualTo(1);
		assertThat(track.physicalPath()).isEqualTo(file);
		assertThat(track.provenance()).isEqualTo(new Provenance(file, ProvenanceState.INTACT));
	}

	private static void writeTag(Path file, String artist, String title, String album, String albumArtist, String year, String track, String disc) throws Exception {
		AudioFile audioFile = AudioFileIO.read(file.toFile());
		Tag tag = audioFile.getTagOrCreateAndSetDefault();
		tag.setField(FieldKey.ARTIST, artist);
		tag.setField(FieldKey.TITLE, title);
		tag.setField(FieldKey.ALBUM, album);
		tag.setField(FieldKey.ALBUM_ARTIST, albumArtist);
		tag.setField(FieldKey.YEAR, year);
		tag.setField(FieldKey.TRACK, track);
		tag.setField(FieldKey.DISC_NO, disc);
		AudioFileIO.write(audioFile);
	}

	private static Path copyFixture(String classpathResource, Path destination) throws Exception {
		try (InputStream in = JaudiotaggerTagReaderTest.class.getResourceAsStream(classpathResource)) {
			Files.copy(in, destination);
		}
		return destination;
	}

	// WAV generated with javax.sound.sampled (no pre-existing RIFF INFO chunk): jaudiotagger chooses an
	// active ID3 tag by default, unlike a WAV with LIST/INFO already present.
	private static Path createMinimalWav(Path destination) throws Exception {
		AudioFormat format = new AudioFormat(8000f, 16, 1, true, false);
		int frames = 1600;
		byte[] silence = new byte[frames * format.getFrameSize()];
		try (ByteArrayInputStream bais = new ByteArrayInputStream(silence); AudioInputStream ais = new AudioInputStream(bais, format, frames)) {
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, destination.toFile());
		}
		return destination;
	}

	@Test
	void id3v1FieldResiduesAfterAControlCharacterAreRemoved() {
		assertThat(JaudiotaggerTagReader.withoutId3v1Residues("Concerto For Group & Orchestra\u0098{\u001e\u0001\u00f4\u00f1")).isEqualTo("Concerto For Group & Orchestra");
		assertThat(JaudiotaggerTagReader.withoutId3v1Residues("Concerto For Group & Orchestra\u00f0t\u001b\u0001\u00f4\u00f1")).isEqualTo("Concerto For Group & Orchestra");
		assertThat(JaudiotaggerTagReader.withoutId3v1Residues("Title\u0000\u0000")).isEqualTo("Title");
		assertThat(JaudiotaggerTagReader.withoutId3v1Residues("Gu\u00f0mundsd\u00f3ttir")).isEqualTo("Gu\u00f0mundsd\u00f3ttir");
	}
}
