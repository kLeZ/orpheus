package org.orpheus.domain.port;

import java.util.List;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseSelector;

/**
 * Lookup of the known releases of a recording, the role of MusicBrainz in the Tier 2 chain (SPEC.md
 * section 4.4, ADR-014): given a recording id already identified by fingerprint, returns the releases
 * the recording appears on, to be passed to {@link ReleaseSelector#choose(List)}. Empty list if the
 * recording is on no known release.
 */
public interface ReleaseLookupPort {

	List<Release> releasesFor(String recordingId);
}
