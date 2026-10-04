package org.orpheus.domain.port;

import java.nio.file.Path;

/**
 * An audio file could not be read at all (corrupted, a format outside the six supported, ADR-002):
 * not the same case as absent, blank or placeholder tags, which {@link TagReaderPort} treats as
 * normal. The caller of the port decides what to do with it (typically a {@code ReviewItem}); it must
 * never be propagated silently as a track lost without a trace.
 */
public final class UnreadableTrackException extends Exception {

	private final Path file;

	public UnreadableTrackException(Path file, String reason, Throwable cause) {
		super(reason, cause);
		this.file = file;
	}

	public Path file() {
		return file;
	}
}
