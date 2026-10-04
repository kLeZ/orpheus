package org.orpheus.acoustid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 * Runs the external {@code fpcalc} binary (Chromaprint) to compute the audio fingerprint of a file, the
 * first step of Tier 2 (SPEC.md section 4.4). It is a binary outside the Java environment, not a
 * library: it may be missing from the PATH or fail on a corrupted file, and this port feeds a pipeline
 * that scans whole messy libraries. A single problematic file must never stop the processing of the
 * others, so every failure becomes {@link Optional#empty()} with a log line, never a propagated
 * exception.
 *
 * <p>If fpcalc cannot read the file, the audio is first decoded with ffmpeg into a temporary WAV
 * (ADR-0023): fpcalc reads badly some MP3s with corrupted frames that ffmpeg decodes, and on the real run
 * two "Empty fingerprint" tracks were recognized this way at 0.94 and 0.99.
 */
@ApplicationScoped
public class FpcalcClient {

	private static final Logger LOG = Logger.getLogger(FpcalcClient.class);

	private static final long DECODING_TIMEOUT_SECONDS = 300;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Optional<FpcalcResult> compute(Path file) {
		return run(file, file).or(() -> {
			LOG.info("fpcalc could not read " + file + ", retrying on the audio decoded with ffmpeg");
			return computeFromDecoding(file);
		});
	}

	/**
	 * Fingerprint of the audio decoded with ffmpeg: the duration is that of the readable samples, useful
	 * when the file header declares a wrong duration (ADR-0023). The temporary WAV is in the system
	 * temporary folder, never near the source, and is deleted right away.
	 */
	public Optional<FpcalcResult> computeFromDecoding(Path file) {
		Path wav = null;
		try {
			wav = Files.createTempFile("orpheus-fpcalc-", ".wav");
			Process ffmpeg = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "quiet", "-y", "-skip_initial_bytes", String.valueOf(id3BeforeRiff(file)), "-i", file.toString(), "-map", "0:a:0", "-c:a", "pcm_s16le", "-f", "wav", wav.toString())
			                                                                                                                                                                                                                                    .redirectErrorStream(true)
			                                                                                                                                                                                                                                    .start();
			ffmpeg.getInputStream().transferTo(OutputStream.nullOutputStream());
			if (!ffmpeg.waitFor(DECODING_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				ffmpeg.destroyForcibly();
				LOG.warn("ffmpeg did not finish within the timeout decoding " + file);
				return Optional.empty();
			}
			if (Files.size(wav) == 0) {
				return Optional.empty();
			}
			return run(wav, file).map(r -> new FpcalcResult(r.fingerprint(), r.durationSeconds(), true));
		} catch (IOException e) {
			LOG.warn("ffmpeg decoding failed for " + file, e);
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} finally {
			if (wav != null) {
				try {
					Files.deleteIfExists(wav);
				} catch (IOException e) {
					LOG.warn("Cannot delete the temporary file " + wav, e);
				}
			}
		}
	}

	/**
	 * Bytes to skip for a file with an ID3v2 tag in front of a RIFF/WAVE container (12 Nashville Pussy MP3s
	 * on the real run): ffmpeg does not recognize the format while the tag is at the start. Zero
	 * otherwise.
	 */
	static long id3BeforeRiff(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] head = in.readNBytes(10);
			if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
				return 0;
			}
			long size = 10 + (((head[6] & 0x7f) << 21) | ((head[7] & 0x7f) << 14) | ((head[8] & 0x7f) << 7) | (head[9] & 0x7f));
			if ((head[5] & 0x10) != 0) {
				size += 10;
			}
			in.skipNBytes(size - 10);
			byte[] riff = in.readNBytes(4);
			return riff.length == 4 && riff[0] == 'R' && riff[1] == 'I' && riff[2] == 'F' && riff[3] == 'F' ? size : 0;
		} catch (IOException e) {
			return 0;
		}
	}

	private Optional<FpcalcResult> run(Path input, Path file) {
		Process process;
		try {
			// Stdout and stderr stay separate on purpose: fpcalc writes diagnostics on stderr (e.g.
			// decoder warnings), which mixed into stdout through redirectErrorStream would corrupt the
			// parsing of the JSON expected on stdout.
			// -ignore-errors: an MP3 with a few corrupted frames makes fpcalc exit with code 3 and no
			// fingerprint; with the option the fingerprint is computed on the rest of the file and it exits
			// with 0. Measured on the 24 failures of the real run of 2026-09-26: 21 recovered (all those
			// with code 3), 3 stay with code 2 ("Empty fingerprint"). The option is not listed by
			// "fpcalc -h" in 1.6.0 but is accepted.
			process = new ProcessBuilder("fpcalc", "-ignore-errors", "-json", input.toString()).start();
		} catch (IOException e) {
			LOG.warn("fpcalc binary not found on the PATH, cannot compute the audio fingerprint of " + file, e);
			return Optional.empty();
		}

		String stdout;
		try {
			// The fpcalc output for a single track is small (a few lines of JSON): it is read entirely
			// before or during waitFor to avoid a deadlock on the pipe buffer if the process ever produced
			// more output than the OS buffer can hold.
			stdout = readAll(process.getInputStream());
			readAll(process.getErrorStream());
			boolean finished = process.waitFor(30, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				LOG.warn("fpcalc did not finish within the timeout for " + file);
				return Optional.empty();
			}
			if (process.exitValue() != 0) {
				LOG.warn("fpcalc exited with code " + process.exitValue() + " for " + file);
				return Optional.empty();
			}
		} catch (IOException e) {
			LOG.warn("I/O error while running fpcalc for " + file, e);
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.warn("fpcalc run interrupted for " + file, e);
			return Optional.empty();
		}

		return parse(stdout, file);
	}

	private Optional<FpcalcResult> parse(String stdout, Path file) {
		try {
			JsonNode node = objectMapper.readTree(stdout);
			JsonNode fingerprintNode = node.get("fingerprint");
			JsonNode durationNode = node.get("duration");
			if (fingerprintNode == null || durationNode == null) {
				LOG.warn("fpcalc output without the expected fields (fingerprint/duration) for " + file);
				return Optional.empty();
			}
			String fingerprint = fingerprintNode.asText();
			int durationSeconds = (int) Math.round(durationNode.asDouble());
			return Optional.of(new FpcalcResult(fingerprint, durationSeconds));
		} catch (IOException e) {
			LOG.warn("fpcalc output is not valid JSON for " + file, e);
			return Optional.empty();
		}
	}

	private static String readAll(InputStream in) throws IOException {
		return new String(in.readAllBytes());
	}
}
