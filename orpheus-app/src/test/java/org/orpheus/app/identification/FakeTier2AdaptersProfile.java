package org.orpheus.app.identification;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Set;

/**
 * Enables the Tier 2 fakes ({@link FingerprintLookupFake}/{@link ReleaseLookupFake}) instead of the real
 * adapters (AcoustID/MusicBrainz), for deterministic tests without network.
 */
public class FakeTier2AdaptersProfile implements QuarkusTestProfile {

	@Override
	public Set<Class<?>> getEnabledAlternatives() {
		return Set.of(FingerprintLookupFake.class, ReleaseLookupFake.class, GenresFake.class, ReleaseGroupSearchFake.class, RecordingSearchFake.class, TracklistAndComposersFake.class);
	}
}
