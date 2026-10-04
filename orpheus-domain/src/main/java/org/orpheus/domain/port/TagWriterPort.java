package org.orpheus.domain.port;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.ingest.TagsToWrite;

/**
 * Writes the tags of ADR-0021 on a library copy, never on the source. Writes the fields of
 * {@link TagsToWrite#values()} and the original values in {@code ORPHEUS_ORIGINALE_<CAMPO>}; every
 * other field of the file stays as it was (rule T25).
 */
public interface TagWriterPort {

	void write(Path copy, TagsToWrite tags) throws TagWriteException;

	final class TagWriteException extends Exception {

		public TagWriteException(Path file, String message, @Nullable Throwable cause) {
			super(file + ": " + message, cause);
		}
	}
}
