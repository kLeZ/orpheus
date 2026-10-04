package org.orpheus.domain.port;

import java.util.List;
import java.util.Optional;

/**
 * Music genres of an album (release group) or of an artist, by descending votes (ADR-0021, Genre
 * section; source: MusicBrainz genres). An empty list is a valid answer (no known genre);
 * {@code Optional.empty()} means an error, not to be made permanent.
 */
public interface GenresPort {

	Optional<List<String>> releaseGroupGenres(String releaseGroupId);

	Optional<List<String>> artistGenres(String artistId);
}
