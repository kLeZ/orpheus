package org.orpheus.domain.port;

import java.util.List;
import java.util.Optional;

/**
 * Track titles of a MusicBrainz release (rule 8, user decision of 2026-10-04): an album grouped by
 * tags and a same-named Tier 2 release are the same record if they share a song.
 * {@code Optional.empty()} means an error.
 */
public interface TracklistPort {

	Optional<List<String>> titles(String releaseId);
}
