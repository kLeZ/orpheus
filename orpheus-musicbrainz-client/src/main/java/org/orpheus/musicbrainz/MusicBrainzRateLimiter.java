package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Explicit limiter with a minimum interval between calls to MusicBrainz (1 request per second,
 * ADR-014): the fault tolerance annotations on {@link MusicBrainzRestClient} handle transient
 * failures, not a constant throttling, so a dedicated component shared among the virtual threads that
 * may call MusicBrainz at the same time is needed (ADR-011). Blocking a virtual thread while waiting is
 * cheap, consistent with that concurrency choice.
 * <p>
 * Independent from the similar limiter of the AcoustID module: no shared dependency between the two
 * client modules (ADR-014).
 */
@ApplicationScoped
public class MusicBrainzRateLimiter {

	private final long minIntervalMillis;
	private long nextAllowedCallMillis = 0L;

	public MusicBrainzRateLimiter() {
		this(1000L);
	}

	public MusicBrainzRateLimiter(long minIntervalMillis) {
		this.minIntervalMillis = minIntervalMillis;
	}

	/**
	 * Blocks the calling thread until the minimum interval since the last allowed call has passed, then
	 * reserves the next turn.
	 */
	public synchronized void awaitTurn() {
		long now = System.currentTimeMillis();
		long wait = nextAllowedCallMillis - now;
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for the MusicBrainz rate limiter", e);
			}
		}
		nextAllowedCallMillis = Math.max(now, nextAllowedCallMillis) + minIntervalMillis;
	}
}
