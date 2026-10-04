package org.orpheus.acoustid;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.orpheus.acoustid.dto.AcoustIdArtistDto;
import org.orpheus.acoustid.dto.AcoustIdLookupResponse;
import org.orpheus.acoustid.dto.AcoustIdRecordingDto;
import org.orpheus.acoustid.dto.AcoustIdResultDto;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Real AcoustID adapter for {@link FingerprintLookupPort} (ADR-014, SPEC.md section 4.4): computes the
 * fingerprint with {@link FpcalcClient}, then queries the public fingerprint database respecting the rate
 * limit on the personal API key (ADR-014). Any failure (fpcalc missing or failed, missing key, network,
 * parsing) becomes an empty result: this port feeds Tier 2 of a pipeline that scans whole libraries, and
 * a single network failure must never block the import of the whole library.
 */
@ApplicationScoped
public class AcoustIdFingerprintLookup implements FingerprintLookupPort {

	private static final Logger LOG = Logger.getLogger(AcoustIdFingerprintLookup.class);
	// "sources": how many times each recording was associated with the fingerprint (rule 11 of ADR-0018).
	private static final String META = "recordings sources";
	private static final int MAX_ATTEMPTS = 4;
	private static final long BACKOFF_BASE_MILLIS = 1000L;
	private static final int DECODED_DURATION_GAP_SECONDS = 5;

	private final FpcalcClient fpcalcClient;
	private final AcoustIdRestClient restClient;
	private final AcoustIdRateLimiter rateLimiter;
	private final Optional<String> apiKey;

	@Inject
	public AcoustIdFingerprintLookup(
	                                 FpcalcClient fpcalcClient, @RestClient AcoustIdRestClient restClient, AcoustIdRateLimiter rateLimiter, @ConfigProperty(name = "acoustid.api-key") Optional<String> apiKey) {
		this.fpcalcClient = fpcalcClient;
		this.restClient = restClient;
		this.rateLimiter = rateLimiter;
		this.apiKey = apiKey;
	}

	@Override
	public FingerprintLookupResult lookup(Track track) {
		String key = apiKey.orElse("");
		if (key.isBlank()) {
			// No fpcalc call: the HTTP lookup cannot work without a key, so there is no point in spawning
			// an external process for nothing.
			LOG.warn("AcoustID API key missing (property 'acoustid.api-key'), fingerprint lookup skipped for " + track.physicalPath());
			return FingerprintLookupResult.empty();
		}

		Optional<FpcalcResult> fingerprint = fpcalcClient.compute(track.physicalPath());
		if (fingerprint.isEmpty()) {
			return FingerprintLookupResult.empty();
		}
		Optional<FingerprintLookupResult> result = lookup(key, fingerprint.get(), track);
		if (result.isEmpty()) {
			return FingerprintLookupResult.empty();
		}
		if (!result.get().candidates().isEmpty() || fingerprint.get().fromDecoding()) {
			return result.get();
		}
		// AcoustID filters by duration (a window of about 7 s): a header declaring a wrong duration (35
		// Sentenced MP3s on the real run) hides a song that is recognized with the decoded duration. The
		// filter of ADR-0018 receives the duration of the successful lookup (ADR-0023).
		Optional<FpcalcResult> decoded = fpcalcClient.computeFromDecoding(track.physicalPath());
		if (decoded.isEmpty() || Math.abs(decoded.get().durationSeconds() - fingerprint.get()
		                                                                               .durationSeconds()) <= DECODED_DURATION_GAP_SECONDS) {
			return result.get();
		}
		LOG.info("No AcoustID result for " + track.physicalPath() + " with " + fingerprint.get()
		                                                                                  .durationSeconds() + " s declared, retrying with " + decoded.get()
		                                                                                                                                              .durationSeconds() + " s decoded");
		return lookup(key, decoded.get(), track).orElse(result.get());
	}

	@Override
	public FingerprintLookupResult lookupWithDuration(Track track, int durationSeconds) {
		String key = apiKey.orElse("");
		if (key.isBlank()) {
			return FingerprintLookupResult.empty();
		}
		return fpcalcClient.compute(track.physicalPath())
		                   .flatMap(f -> lookup(key, new FpcalcResult(f.fingerprint(), durationSeconds, f.fromDecoding()), track))
		                   .orElse(FingerprintLookupResult.empty());
	}

	// Empty if the lookup fails after the attempts; a result without candidates if AcoustID finds nothing.
	private Optional<FingerprintLookupResult> lookup(String key, FpcalcResult fingerprint, Track track) {
		Exception lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				rateLimiter.awaitTurn();
				AcoustIdLookupResponse response = restClient.lookup(key, fingerprint.fingerprint(), fingerprint.durationSeconds(), META);
				return Optional.of(new FingerprintLookupResult(Optional.of(fingerprint.durationSeconds()), candidatesFrom(response)));
			} catch (Exception e) {
				lastError = e;
				if (!retryable(e) || attempt == MAX_ATTEMPTS) {
					break;
				}
				long wait = BACKOFF_BASE_MILLIS * (1L << (attempt - 1));
				try {
					Thread.sleep(wait);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					LOG.warn("Interrupted while waiting to retry AcoustID for " + track.physicalPath());
					break;
				}
			}
		}
		LOG.warn("AcoustID lookup failed after " + MAX_ATTEMPTS + " attempts for " + track.physicalPath(), lastError);
		return Optional.empty();
	}

	private static boolean retryable(Exception e) {
		if (e instanceof WebApplicationException wae) {
			int status = wae.getResponse().getStatus();
			return status == 429 || status == 502 || status == 503 || status == 504;
		}
		return true;
	}

	private static List<CandidateRecording> candidatesFrom(AcoustIdLookupResponse response) {
		if (response == null || response.results() == null) {
			return List.of();
		}
		// Dedup by id keeping the insertion order, that is the descending score order already given by the
		// results[] array of the AcoustID API.
		Set<String> seen = new HashSet<>();
		List<CandidateRecording> candidates = new ArrayList<>();
		for (AcoustIdResultDto result : response.results()) {
			if (result.recordings() == null) {
				continue;
			}
			for (AcoustIdRecordingDto recording : result.recordings()) {
				if (recording.id() == null || recording.id().isBlank() || !seen.add(recording.id())) {
					continue;
				}
				List<String> artists = recording.artists() == null ? List.of() : recording.artists()
				                                                                          .stream()
				                                                                          .map(AcoustIdArtistDto::name)
				                                                                          .filter(Objects::nonNull)
				                                                                          .toList();
				Optional<Integer> duration = Optional.ofNullable(recording.duration()).map(d -> (int) Math.round(d));
				candidates.add(new CandidateRecording(recording.id(), recording.title(), artists, duration, Optional.ofNullable(result.score()), Optional.ofNullable(recording.sources())));
			}
		}
		return candidates;
	}
}
