package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.orpheus.domain.port.RecordingSearchPort;
import org.orpheus.musicbrainz.dto.ArtistCreditDto;
import org.orpheus.musicbrainz.dto.RecordingSearchDto;

/** Search of recordings by artist and title on the public MusicBrainz API (ADR-0023, intervention 4). */
@ApplicationScoped
public class MusicBrainzRecordingSearch implements RecordingSearchPort {

	private static final int LIMIT = 25;

	@Inject
	@RestClient
	MusicBrainzRestClient client;

	@Inject
	MusicBrainzRateLimiter rateLimiter;

	@Override
	public Optional<List<FoundRecording>> search(String artist, String title) {
		String query = "recording:\"" + MusicBrainzReleaseGroupSearch.phrase(title) + "\" AND artist:\"" + MusicBrainzReleaseGroupSearch.phrase(artist) + "\"";
		return MusicBrainzReleaseGroupSearch.withRetries(rateLimiter, () -> translate(client.searchRecordings(query, LIMIT, "json")), title, artist);
	}

	static List<FoundRecording> translate(RecordingSearchDto dto) {
		if (dto == null || dto.recordings() == null) {
			return List.of();
		}
		return dto.recordings().stream().filter(v -> v.title() != null).map(v -> {
			StringBuilder name = new StringBuilder();
			for (ArtistCreditDto c : v.artistCredit() == null ? List.<ArtistCreditDto>of() : v.artistCredit()) {
				name.append(Objects.requireNonNullElse(c.name(), ""))
				    .append(Objects.requireNonNullElse(c.joinphrase(), ""));
			}
			return new FoundRecording(v.title(), name.toString(), Optional.ofNullable(v.length())
			                                                              .map(ms -> (int) Math.round(ms / 1000.0)));
		}).toList();
	}
}
