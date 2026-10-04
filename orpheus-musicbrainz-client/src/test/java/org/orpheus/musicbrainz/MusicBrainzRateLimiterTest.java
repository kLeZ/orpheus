package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Checks the behavior of the limiter in isolation (no {@code @QuarkusTest}: it is a POJO independent of
 * the CDI context), with a reduced minimum interval passed to the constructor so as not to slow the
 * suite down.
 */
class MusicBrainzRateLimiterTest {

	@Test
	void theSecondCallWaitsAtLeastTheConfiguredMinimumInterval() {
		long minIntervalMillis = 200L;
		MusicBrainzRateLimiter sut = new MusicBrainzRateLimiter(minIntervalMillis);

		sut.awaitTurn();
		long start = System.currentTimeMillis();
		sut.awaitTurn();
		long delta = System.currentTimeMillis() - start;

		assertThat(delta).isGreaterThanOrEqualTo(minIntervalMillis);
	}
}
