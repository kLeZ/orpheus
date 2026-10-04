package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * File cache of the AcoustID and MusicBrainz responses, to repeat check runs on the whole library
 * without redoing tens of minutes of rate-limited calls (corrective plan, phase 0). Active only if
 * {@code orpheus.cache-dir} is set: without it, the decorators always delegate. Only non-empty results
 * are stored, because the adapters return an empty result also for a network or fpcalc error, which
 * must not be made permanent. No expiry: to invalidate, delete the folder.
 */
@ApplicationScoped
public class ExternalResponseCache {

	private static final Logger LOG = Logger.getLogger(ExternalResponseCache.class);

	@ConfigProperty(name = "orpheus.cache-dir")
	Optional<Path> folder;

	@Inject
	ObjectMapper objectMapper;

	boolean enabled() {
		return folder.isPresent();
	}

	<T> Optional<T> read(String category, String key, TypeReference<T> type) {
		Path file = fileOf(category, key);
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(file.toFile(), type));
		} catch (IOException e) {
			LOG.warn("Unreadable cache entry, ignored: " + file, e);
			return Optional.empty();
		}
	}

	void write(String category, String key, Object value) {
		Path file = fileOf(category, key);
		try {
			Files.createDirectories(file.getParent());
			// Written to a temporary file and renamed: an interrupted run leaves no truncated entries.
			Path temporary = Files.createTempFile(file.getParent(), "entry", ".tmp");
			objectMapper.writeValue(temporary.toFile(), value);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.warn("Cannot write the cache entry " + file, e);
		}
	}

	/**
	 * Key of a local file: absolute path, size and modification time, so a modified file changes key.
	 */
	static Optional<String> fileKey(Path file) {
		try {
			return Optional.of(file.toAbsolutePath().normalize() + "|" + Files.size(file) + "|" + Files
			                                                                                           .getLastModifiedTime(file)
			                                                                                           .toMillis());
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private Path fileOf(String category, String key) {
		return folder.orElseThrow().resolve(category).resolve(hash(key) + ".json");
	}

	private static String hash(String key) {
		try {
			return HexFormat.of()
			                .formatHex(MessageDigest.getInstance("SHA-256")
			                                        .digest(key.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must always be available in the JVM", e);
		}
	}
}
