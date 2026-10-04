package org.orpheus.acoustid;

/**
 * Result of the Chromaprint fingerprint computation through {@code fpcalc}: the text fingerprint and the
 * duration of the track in seconds, as required by the {@code fingerprint} and {@code duration}
 * parameters of the AcoustID lookup (SPEC.md section 4.4). {@code fromDecoding} is true when the
 * fingerprint comes from audio decoded first with ffmpeg: the duration is then that of the samples
 * actually readable, not the one declared by the file (ADR-0023).
 */
public record FpcalcResult(String fingerprint, int durationSeconds, boolean fromDecoding) {

	public FpcalcResult(String fingerprint, int durationSeconds) {
		this(fingerprint, durationSeconds, false);
	}
}
