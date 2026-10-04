package org.orpheus.domain.port;

import java.util.List;
import java.util.Optional;

/**
 * Authors of a recording according to MusicBrainz, from the "composer" and "writer" relations of the
 * performed work (user decision of 2026-10-04: composer on every track, full names in MusicBrainz
 * order). {@code Optional.empty()} means an error; an empty list, no known author.
 */
public interface ComposersPort {

	Optional<List<String>> composers(String recordingId);
}
