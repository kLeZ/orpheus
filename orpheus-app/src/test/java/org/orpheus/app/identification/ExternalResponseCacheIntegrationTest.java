package org.orpheus.app.identification;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * The cache decorators (corrective plan, phase 0) wrap the Tier 2 ports: with {@code orpheus.cache-dir}
 * set, a second request is answered from the cache even when the underlying adapter would no longer
 * answer (here: the emptied fake).
 */
@QuarkusTest
@TestProfile(ExternalResponseCacheIntegrationTest.WithCacheProfile.class)
class ExternalResponseCacheIntegrationTest {

	static final Path CACHE_FOLDER = Path.of("target", "cache-test");

	public static class WithCacheProfile implements QuarkusTestProfile {

		@Override
		public Set<Class<?>> getEnabledAlternatives() {
			return Set.of(FingerprintLookupFake.class, ReleaseLookupFake.class);
		}

		@Override
		public Map<String, String> getConfigOverrides() {
			return Map.of("orpheus.cache-dir", CACHE_FOLDER.toString());
		}
	}

	@Inject
	FingerprintLookupPort fingerprintLookup;

	@Inject
	ReleaseLookupPort releaseLookup;

	@Inject
	FingerprintLookupFake fingerprintsFake;

	@Inject
	ReleaseLookupFake releasesFake;

	@BeforeEach
	void clear() throws IOException {
		fingerprintsFake.clear();
		releasesFake.clear();
		if (Files.exists(CACHE_FOLDER)) {
			try (Stream<Path> file = Files.walk(CACHE_FOLDER)) {
				file.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	@Test
	void aSecondFingerprintLookupIsAnsweredFromTheCache(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("t.mp3"), "content");
		Track track = track(file);
		fingerprintsFake.set(file, List.of("rec-1"), "Bon Jovi");

		var first = fingerprintLookup.lookup(track);
		fingerprintsFake.clear();
		var second = fingerprintLookup.lookup(track);

		assertThat(second).isEqualTo(first);
		assertThat(second.candidates()).extracting(CandidateRecording::recordingId).containsExactly("rec-1");
	}

	@Test
	void anEmptyResultIsNotCached(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("t.mp3"), "content");
		Track track = track(file);

		assertThat(fingerprintLookup.lookup(track).candidates()).isEmpty();
		fingerprintsFake.set(file, List.of("rec-2"), "Bon Jovi");

		assertThat(fingerprintLookup.lookup(track).candidates()).extracting(CandidateRecording::recordingId)
		                                                        .containsExactly("rec-2");
	}

	@Test
	void aSecondReleaseLookupIsAnsweredFromTheCacheWithAllTheFields() {
		Release release = new Release("rel", ReleaseType.ALBUM, ReleaseDate.exact(1992, 10, 30), List.of("Bon Jovi"), true);
		releasesFake.set("rec-1", List.of(release, new Release("rel-2", ReleaseType.EP, ReleaseDate.yearOnly(1993))));

		var first = releaseLookup.releasesFor("rec-1");
		releasesFake.clear();
		var second = releaseLookup.releasesFor("rec-1");

		assertThat(second).isEqualTo(first).hasSize(2).first().isEqualTo(release);
	}

	private static Track track(Path file) {
		var metadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.unknown());
		return new Track(file, metadata, new Provenance(file, ProvenanceState.INTACT));
	}
}
