package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Checks that the default value really comes from the MicroProfile Config configuration
 * ({@code musicbrainz.user-agent}, no property set in the tests, so the {@code defaultValue} applies)
 * and that {@link MusicBrainzUserAgentConfiguration#override(String)} really changes what
 * {@link MusicBrainzUserAgentConfiguration#value()} returns, the mechanism the per-run CLI contact
 * override depends on.
 * <p>
 * The bean is {@code @ApplicationScoped}: a single instance survives all the test methods of this class
 * (the CDI context of {@code @QuarkusTest} does not restart at every {@code @Test}). The
 * {@code override} test restores the original value at the end so as not to leave mutated state that
 * the execution order of the other tests could otherwise observe.
 */
@QuarkusTest
class MusicBrainzUserAgentConfigurationTest {

	@Inject
	MusicBrainzUserAgentConfiguration sut;

	@Test
	void theDefaultValueComesFromTheConfiguration() {
		assertThat(sut.value()).isEqualTo("Orpheus/0.1 (contact not configured)");
	}

	@Test
	void overrideChangesTheValueReturnedByValue() {
		String originalValue = sut.value();
		try {
			sut.override("Orpheus/0.1 (test@example.org)");

			assertThat(sut.value()).isEqualTo("Orpheus/0.1 (test@example.org)");
		} finally {
			sut.override(originalValue);
		}
	}
}
