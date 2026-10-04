package org.orpheus.domain.port;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Hash of the decoded audio of a file, independent of the tags (ADR-0019): two files with the same
 * hash are the same audio even if the file bytes differ in the tags. The duration is needed too, to
 * be reported in alternative version review items.
 */
public interface AudioHashPort {

	record Hash(String hash, Optional<Integer> durationSeconds) {
	}

	Optional<Hash> hashOf(Path file);
}
