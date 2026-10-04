package org.orpheus.acoustid;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Uses the real {@code fpcalc} binary (already installed in the environment) on an audio file generated
 * on the fly with {@code ffmpeg}, the same pattern used in
 * {@code orpheus-app/src/test/java/org/orpheus/app/ImportCommandIntegrationTest.java}: no mock for a
 * component that is essentially a wrapper around an external process, the risk to check is exactly the
 * real integration (parsing the true JSON output of fpcalc).
 */
class FpcalcClientTest {

	@Test
	void computesFingerprintAndDurationForARealAudioFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sample.mp3");
		generateMp3WithTone(file);

		Optional<FpcalcResult> result = new FpcalcClient().compute(file);

		assertThat(result).isPresent();
		assertThat(result.get().fingerprint()).isNotBlank();
		assertThat(result.get().durationSeconds()).isGreaterThan(0);
	}

	@Test
	void fingerprintFromTheDecodedAudioWithTheSampleDuration(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sample.mp3");
		generateMp3WithTone(file);

		Optional<FpcalcResult> result = new FpcalcClient().computeFromDecoding(file);

		assertThat(result).isPresent();
		assertThat(result.get().fromDecoding()).isTrue();
		assertThat(result.get().fingerprint()).isNotBlank();
		assertThat(result.get().durationSeconds()).isEqualTo(3);
		try (var temporaryFiles = java.nio.file.Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			assertThat(temporaryFiles.filter(f -> f.getFileName().toString().startsWith("orpheus-fpcalc-"))).isEmpty();
		}
	}

	@Test
	void returnsEmptyForAMissingFileWithoutThrowing(@TempDir Path dir) {
		Path missing = dir.resolve("missing.mp3");

		Optional<FpcalcResult> result = new FpcalcClient().compute(missing);

		assertThat(result).isEmpty();
	}

	// Chromaprint cannot produce a fingerprint from silent audio ("Empty fingerprint", exit code 2,
	// unlike jaudiotagger or the decoder, for which the silence generated with anullsrc is enough): a real
	// waveform is needed, a sine tone, and at least about 3 seconds of duration; below that fpcalc fails
	// with the same error.
	private static void generateMp3WithTone(Path destination) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(
		        "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-codec:a", "libmp3lame", destination
		                                                                                                                   .toString())
		                                                                                                                               .redirectErrorStream(true)
		                                                                                                                               .start();
		if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
			throw new IOException("ffmpeg did not generate the test file: " + destination);
		}
	}
}
