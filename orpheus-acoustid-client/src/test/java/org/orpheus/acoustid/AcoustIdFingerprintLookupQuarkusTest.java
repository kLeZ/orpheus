package org.orpheus.acoustid;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

/**
 * Full path with real CDI: real {@link FpcalcClient} on a file generated with ffmpeg (same pattern as
 * {@link FpcalcClientTest} and {@code ImportCommandIntegrationTest} in orpheus-app), HTTP lookup against
 * an embedded JDK {@link com.sun.net.httpserver.HttpServer} started by {@link AcoustIdStub}, a
 * {@link QuarkusTestResourceLifecycleManager} that Quarkus invokes before booting the test application,
 * so the actual port (chosen by the operating system to avoid collisions) is known in time to inject
 * {@code quarkus.rest-client.acoustid-api.url} as a config override.
 */
@QuarkusTest
@QuarkusTestResource(AcoustIdFingerprintLookupQuarkusTest.AcoustIdStub.class)
class AcoustIdFingerprintLookupQuarkusTest {

	@Inject
	AcoustIdFingerprintLookup port;

	@Test
	void mapsTheRecordingsDeduplicatingInScoreOrderWithMetadataAndDuration(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("track.mp3");
		generateMp3WithTone(file);

		FingerprintLookupResult result = port.lookup(sampleTrack(file));

		assertThat(result.candidates()).extracting(CandidateRecording::recordingId)
		                               .containsExactly("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
		CandidateRecording first = result.candidates().get(0);
		assertThat(first.title()).isEqualTo("Bed of Roses");
		assertThat(first.artists()).containsExactly("Bon Jovi");
		assertThat(first.durationSeconds()).hasValue(394);
		assertThat(first.score()).hasValue(0.984);
		CandidateRecording second = result.candidates().get(1);
		assertThat(second.title()).isNull();
		assertThat(second.artists()).isEmpty();
		assertThat(second.durationSeconds()).isEmpty();
		assertThat(second.score()).hasValue(0.984);
		assertThat(result.fileDurationSeconds()).isPresent();
	}

	private static Track sampleTrack(Path path) {
		TrackMetadata metadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds
		                                                                                                .unknown());
		return new Track(path, metadata, new Provenance(path, ProvenanceState.INTACT));
	}

	// See the equivalent comment in FpcalcClientTest: Chromaprint produces no fingerprint from silent
	// audio, a real tone and at least about 3 seconds of duration are needed.
	private static void generateMp3WithTone(Path destination) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(
		        "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-codec:a", "libmp3lame", destination
		                                                                                                                   .toString())
		                                                                                                                               .redirectErrorStream(true)
		                                                                                                                               .start();
		if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
			throw new IOException("ffmpeg did not generate the test file: " + destination);
		}
	}

	/**
	 * Canonical AcoustID response: two results, the first with two recordings (one repeated in the second
	 * result, to check the deduplication), sorted by descending score as the {@code results[]} array of
	 * the real API; the list expected by the test keeps this order.
	 */
	public static class AcoustIdStub implements QuarkusTestResourceLifecycleManager {

		private HttpServer server;

		@Override
		public Map<String, String> start() {
			try {
				server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			} catch (IOException e) {
				throw new RuntimeException("Cannot start the test HTTP server", e);
			}
			server.createContext("/v2/lookup", exchange -> {
				String body = """
				        {
				          "status": "ok",
				          "results": [
				            {
				              "id": "score-1",
				              "score": 0.984,
				              "recordings": [
				                {"id": "11111111-1111-1111-1111-111111111111", "title": "Bed of Roses",
				                 "artists": [{"id": "a", "name": "Bon Jovi"}], "duration": 394.0},
				                {"id": "22222222-2222-2222-2222-222222222222"}
				              ]
				            },
				            {
				              "id": "score-2",
				              "score": 0.581,
				              "recordings": [
				                {"id": "11111111-1111-1111-1111-111111111111"}
				              ]
				            }
				          ]
				        }
				        """;
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, bytes.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			});
			server.start();
			int port = server.getAddress().getPort();
			return Map.of(
			        "quarkus.rest-client.acoustid-api.url", "http://localhost:" + port, "acoustid.api-key", "test-key");
		}

		@Override
		public void stop() {
			if (server != null) {
				server.stop(0);
			}
		}
	}
}
