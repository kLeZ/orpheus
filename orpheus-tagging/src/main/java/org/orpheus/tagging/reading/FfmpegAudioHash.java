package org.orpheus.tagging.reading;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jaudiotagger.audio.AudioFileIO;
import org.jboss.logging.Logger;
import org.orpheus.domain.port.AudioHashPort;

/**
 * Hash of the audio decoded with ffmpeg ({@code -map 0:a -f md5}): the hash of the samples, not of the
 * file bytes, so two copies differing only in the tags have the same hash (ADR-0019; 165 such pairs on
 * the real run). Without ffmpeg, or if decoding fails, it falls back on the hash of the file bytes: two
 * byte-identical files are still recognized as identical, two files differing only in the tags come out
 * different (one more version instead of a duplicate, never the other way round).
 */
@ApplicationScoped
public class FfmpegAudioHash implements AudioHashPort {

	private static final Logger LOG = Logger.getLogger(FfmpegAudioHash.class);
	private static final long TIMEOUT_SECONDS = 300;

	@Override
	public Optional<Hash> hashOf(Path file) {
		Optional<String> hash = audioHash(file).or(() -> byteHash(file));
		return hash.map(h -> new Hash(h, duration(file)));
	}

	private static Optional<String> audioHash(Path file) {
		try {
			Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(), "-map", "0:a", "-f", "md5", "-").redirectError(ProcessBuilder.Redirect.DISCARD)
			                                                                                                               .start();
			String output;
			try (InputStream in = p.getInputStream()) {
				output = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
			}
			if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return Optional.empty();
			}
			return p.exitValue() == 0 && output.startsWith("MD5=") ? Optional.of(output.substring(4)) : Optional.empty();
		} catch (IOException e) {
			LOG.debug("ffmpeg not available, hash on the file bytes for " + file, e);
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	private static Optional<String> byteHash(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[65536];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
			return Optional.of("file:" + HexFormat.of().formatHex(digest.digest()));
		} catch (IOException | NoSuchAlgorithmException e) {
			LOG.warn("Cannot compute the hash of " + file, e);
			return Optional.empty();
		}
	}

	private static Optional<Integer> duration(Path file) {
		try {
			return Optional.of(AudioFileIO.read(file.toFile()).getAudioHeader().getTrackLength());
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
