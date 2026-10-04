package org.orpheus.tagging.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.domain.port.AudioAnalysisPort.Analysis;

// The lavfi tone has amplitude 1/8: volume=4 brings it to about -9 dBFS, the level of real music.
// Damage built with ffmpeg on the three main formats (ADR-0023): a clean cut to digital silence,
// corrupted bytes in the middle of the file, a file truncated halfway like an interrupted download.
class FfmpegAudioAnalysisTest {

	private final FfmpegAudioAnalysis analysis = new FfmpegAudioAnalysis();

	@Test
	void anIntactFileWithoutErrorsOrDropouts(@TempDir Path dir) throws Exception {
		Path file = generate(dir.resolve("intact.flac"), "sine=frequency=440:duration=20,volume=4");

		Analysis a = analysis.analyze(file).orElseThrow();

		assertThat(a.decodingErrors()).isZero();
		assertThat(a.dropouts()).isEmpty();
		assertThat(a.decodedDurationSeconds()).isCloseTo(20.0, within(0.1));
		assertThat(a.declaredDurationSeconds()).hasValueSatisfying(d -> assertThat(d).isCloseTo(20.0, within(0.1)));
		assertThat(a.finalLevelDb()).isGreaterThan(-20.0);
	}

	@Test
	void cleanCutToDigitalSilenceInAllFormats(@TempDir Path dir) throws Exception {
		for (String format : new String[]{
		                                  "mp3",
		                                  "flac",
		                                  "ogg"
		}) {
			Path file = generate(dir.resolve("dropout." + format), "sine=frequency=440:duration=20,volume=4,volume=enable='between(t,8,9.5)':volume=0");

			Analysis a = analysis.analyze(file).orElseThrow();

			assertThat(a.dropouts()).as(format).hasSize(1);
			assertThat(a.dropouts().get(0).startSeconds()).as(format).isCloseTo(8.0, within(0.1));
			assertThat(a.dropouts().get(0).durationSeconds()).as(format).isCloseTo(1.5, within(0.15));
			assertThat(a.dropouts().get(0).precedingLevelDb()).as(format).isGreaterThan(-20.0);
		}
	}

	@Test
	void corruptedBytesProduceDecodingErrors(@TempDir Path dir) throws Exception {
		for (String format : new String[]{
		                                  "mp3",
		                                  "flac",
		                                  "ogg"
		}) {
			Path file = generate(dir.resolve("corrupted." + format), "sine=frequency=440:duration=30,volume=4");
			byte[] b = Files.readAllBytes(file);
			for (double k : new double[]{
			                             0.3,
			                             0.5,
			                             0.7
			}) {
				int i = (int) (b.length * k);
				Arrays.fill(b, i, Math.min(b.length, i + 4000), (byte) 0x5a);
			}
			Files.write(file, b);

			assertThat(analysis.analyze(file).orElseThrow().decodingErrors()).as(format).isPositive();
		}
	}

	@Test
	void aTruncatedFlacDeclaresTheWholeDuration(@TempDir Path dir) throws Exception {
		Path file = generate(dir.resolve("truncated.flac"), "sine=frequency=440:duration=30,volume=4");
		byte[] b = Files.readAllBytes(file);
		Files.write(file, Arrays.copyOf(b, (int) (b.length * 0.6)));

		Analysis a = analysis.analyze(file).orElseThrow();

		assertThat(a.declaredDurationSeconds()).hasValueSatisfying(d -> assertThat(d).isCloseTo(30.0, within(0.1)));
		assertThat(a.decodedDurationSeconds()).isLessThan(25.0);
	}

	// Nashville Pussy "High As Hell": MP3 in a RIFF/WAVE container with an ID3v2 tag at the start.
	@Test
	void mp3InRiffWithLeadingId3Tag(@TempDir Path dir) throws Exception {
		Path mp3 = generate(dir.resolve("tone.mp3"), "sine=frequency=440:duration=10,volume=4");
		Path wav = dir.resolve("riff.wav");
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-i", mp3.toString(), "-c:a", "copy", "-f", "wav", wav.toString()).redirectErrorStream(true)
		                                                                                                                                .start();
		p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
		assertThat(p.waitFor()).isZero();
		byte[] id3 = new byte[30];
		id3[0] = 'I';
		id3[1] = 'D';
		id3[2] = '3';
		id3[3] = 4;
		id3[9] = 20;
		Path file = dir.resolve("01 - Struttin' Cock.mp3");
		Files.write(file, id3);
		Files.write(file, Files.readAllBytes(wav), java.nio.file.StandardOpenOption.APPEND);

		assertThat(FfmpegAudioAnalysis.id3BeforeRiff(file)).isEqualTo(30);
		assertThat(analysis.analyze(file).orElseThrow().decodedDurationSeconds()).isCloseTo(10.0, within(0.2));
		assertThat(FfmpegAudioAnalysis.id3BeforeRiff(mp3)).isZero();
	}

	// "Farther Away": SIT tones of 0.25-0.4 s at -8 dBFS in the middle of the song.
	@Test
	void sitToneSequenceInTheMiddleOfTheSong(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sit.flac");
		String song = "anoisesrc=d=5:c=pink:a=0.3";
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", song, "-f", "lavfi", "-i", "sine=f=913.8:d=0.33,volume=3", "-f", "lavfi", "-i", "sine=f=1370.6:d=0.33,volume=3", "-f", "lavfi", "-i", "sine=f=1776.7:d=0.33,volume=3", "-f", "lavfi", "-i", song, "-filter_complex", "[0][1][2][3][4]concat=n=5:v=0:a=1", "-ac", "2", "-ar", "44100", file
		                                                                                                                                                                                                                                                                                                                                                                             .toString()).redirectErrorStream(true)
		                                                                                                                                                                                                                                                                                                                                                                                         .start();
		p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
		assertThat(p.waitFor()).isZero();

		Analysis a = analysis.analyze(file).orElseThrow();

		assertThat(a.lineTones()).hasSize(1);
		assertThat(a.lineTones().get(0)).isCloseTo(5.0, within(0.1));
	}

	@Test
	void aSinglePureToneIsNotASitSequence(@TempDir Path dir) throws Exception {
		Path file = generate(dir.resolve("tone.flac"), "sine=frequency=913.8:duration=10,volume=4");

		assertThat(analysis.analyze(file).orElseThrow().lineTones()).isEmpty();
	}

	@Test
	void aNonAudioFileGivesEmpty(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("fake.mp3");
		Files.writeString(file, "these are not audio");

		assertThat(analysis.analyze(file)).isEqualTo(Optional.empty());
	}

	private static Path generate(Path file, String source) throws IOException, InterruptedException {
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", source, "-ac", "2", "-ar", "44100", file.toString()).redirectErrorStream(true)
		                                                                                                                                       .start();
		p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
		if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
			throw new IOException("ffmpeg did not generate " + file);
		}
		return file;
	}
}
