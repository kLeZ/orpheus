package org.orpheus.acoustid;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Explicit client-side limiter towards AcoustID (ADR-014): it guarantees a minimum interval between
 * consecutive calls by blocking the calling thread, not delegated to the fault tolerance annotations of
 * {@link AcoustIdRestClient}, which handle retries on failure, not constant throttling. With the model of
 * one virtual thread per track (ADR-011) blocking the calling thread is cheap: no permit pool nor
 * scheduled release thread is needed, a mutex and the timestamp of the last call shared among all the
 * concurrent calls to this service are enough (which is why it is a single {@code @ApplicationScoped}
 * bean, injected wherever needed).
 */
@ApplicationScoped
public class AcoustIdRateLimiter {

	private static final long DEFAULT_INTERVAL_MS = 1000;

	private final long minIntervalMs;
	private long lastCallNanos = Long.MIN_VALUE;

	public AcoustIdRateLimiter() {
		this(DEFAULT_INTERVAL_MS);
	}

	public AcoustIdRateLimiter(long minIntervalMs) {
		this.minIntervalMs = minIntervalMs;
	}

	/**
	 * Blocks the calling thread until the minimum interval since the last authorized call has passed,
	 * then records the current turn as "last call" before returning, so that two threads waiting on the
	 * same lock queue one after the other instead of being released together.
	 */
	public synchronized void awaitTurn() {
		while (true) {
			long now = System.nanoTime();
			long elapsedMs = (now - lastCallNanos) / 1_000_000;
			if (lastCallNanos == Long.MIN_VALUE || elapsedMs >= minIntervalMs) {
				lastCallNanos = now;
				return;
			}
			long toWaitMs = minIntervalMs - elapsedMs;
			try {
				Thread.sleep(toWaitMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				lastCallNanos = System.nanoTime();
				return;
			}
		}
	}
}
