package org.orpheus.tagging.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegAudioHashTest {

	private final FfmpegAudioHash sut = new FfmpegAudioHash();

	private static Path copyFile(String resource, Path destination) throws Exception {
		try (InputStream in = FfmpegAudioHashTest.class.getResourceAsStream(resource)) {
			Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		return destination;
	}

	// Two copies of the same audio with different tags: same hash (the Deep Purple duplicates).
	@Test
	void sameAudioWithDifferentTagsHasTheSameHash(@TempDir Path dir) throws Exception {
		Path a = copyFile("/audio/minimal.mp3", dir.resolve("a.mp3"));
		Path b = copyFile("/audio/minimal.mp3", dir.resolve("b.mp3"));
		AudioFile f = AudioFileIO.read(b.toFile());
		f.getTagOrCreateAndSetDefault().setField(FieldKey.COMMENT, "different tag");
		AudioFileIO.write(f);

		assertThat(Files.size(a)).isNotEqualTo(Files.size(b));
		assertThat(sut.hashOf(a).orElseThrow().hash()).isEqualTo(sut.hashOf(b).orElseThrow().hash());
	}

	// The fixtures are silence, identical once decoded in every format: a real tone is needed.
	private static Path tone(Path destination) throws Exception {
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", destination.toString()).inheritIO()
		                                                                                                                                           .start();
		assertThat(p.waitFor()).isZero();
		return destination;
	}

	@Test
	void differentFormatsHaveDifferentHashes(@TempDir Path dir) throws Exception {
		Path mp3 = tone(dir.resolve("a.mp3"));
		Path flac = tone(dir.resolve("a.flac"));

		assertThat(sut.hashOf(mp3).orElseThrow().hash()).isNotEqualTo(sut.hashOf(flac).orElseThrow().hash());
		assertThat(sut.hashOf(mp3).orElseThrow().durationSeconds()).isPresent();
	}
}
