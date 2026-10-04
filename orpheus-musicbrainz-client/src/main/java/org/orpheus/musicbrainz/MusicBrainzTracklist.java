package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.orpheus.domain.port.TracklistPort;
import org.orpheus.musicbrainz.dto.MediumDto;
import org.orpheus.musicbrainz.dto.RecordingRelationsDto;
import org.orpheus.musicbrainz.dto.ReleaseDto;
import org.orpheus.musicbrainz.dto.TrackDto;

/** Tracklist of a release on the public MusicBrainz API (rule 8). */
@ApplicationScoped
public class MusicBrainzTracklist implements TracklistPort {

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public Optional<List<String>> titles(String releaseId) {
		return MusicBrainzReleaseGroupSearch.withRetries(rateLimiter, () -> titlesOf(client.release(releaseId, "recordings", "json")), releaseId, "tracklist");
	}

	static List<String> titlesOf(ReleaseDto release) {
		List<String> titles = new ArrayList<>();
		if (release == null || release.media() == null) {
			return titles;
		}
		for (MediumDto medium : release.media()) {
			for (TrackDto track : medium.tracks() == null ? List.<TrackDto>of() : medium.tracks()) {
				if (track.title() != null) {
					titles.add(track.title());
				}
			}
		}
		return titles;
	}

	/** Authors from the "composer" and "writer" relations of the performed works, in MusicBrainz order. */
	static List<String> composersOf(RecordingRelationsDto dto) {
		Set<String> names = new LinkedHashSet<>();
		if (dto == null || dto.relations() == null) {
			return List.of();
		}
		for (RecordingRelationsDto.Relation r : dto.relations()) {
			if (!"performance".equals(r.type()) || r.work() == null || r.work().relations() == null) {
				continue;
			}
			for (RecordingRelationsDto.Relation author : r.work().relations()) {
				if (("composer".equals(author.type()) || "writer".equals(author.type())) && author.artist() != null && author.artist()
				                                                                                                             .name() != null) {
					names.add(author.artist().name());
				}
			}
		}
		return List.copyOf(names);
	}
}
