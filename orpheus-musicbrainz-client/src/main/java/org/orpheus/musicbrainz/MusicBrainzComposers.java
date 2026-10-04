package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.orpheus.domain.port.ComposersPort;

/** Authors of a recording on the public MusicBrainz API (user decision of 2026-10-04). */
@ApplicationScoped
public class MusicBrainzComposers implements ComposersPort {

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public Optional<List<String>> composers(String recordingId) {
		return MusicBrainzReleaseGroupSearch.withRetries(rateLimiter, () -> MusicBrainzTracklist.composersOf(client.relations(recordingId, "work-rels+work-level-rels+artist-rels", "json")), recordingId, "composers");
	}
}
