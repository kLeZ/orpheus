package org.orpheus.domain.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.orpheus.domain.port.AudioAnalysisPort;

/**
 * Defects of a damaged audio file, from the measures on the decoded audio (ADR-0023). The thresholds
 * are calibrated on the 2204 files of the user's library on 2026-10-04: the numbers in the comments
 * are those of that measurement.
 */
public final class AudioIntegrity {

	// Two lines per unreadable MP3 frame ("Header missing" and the packet error): 157 MP3s have 2,
	// almost always junk at the edges of the file; from 40 lines (about 20 frames) 16 files remain.
	static final int MP3_ERRORS = 40;
	// Duration of an MP3 frame of 1152 samples at 44.1 kHz, to estimate the audio lost.
	private static final double SECONDS_PER_MP3_FRAME = 1152.0 / 44100.0;
	static final double TRUNCATION_GAP_SECONDS = 5.0;
	// A truncated MP3 breaks off; an intact one with a wrong Xing header ends in a fade or in digital
	// silence (Sentenced, down to -27.9 dBFS).
	static final double TRUNCATED_FINAL_LEVEL_DB = -20.0;
	// The duration tolerance of ADR-0018 between file and recording.
	static final double RECORDING_GAP_SECONDS = 15.0;
	// A dropout is a clean cut: musical pauses reach silence by fading. The same break of Pain Of
	// Salvation "Nightmist" measures -17.7 dBFS in one edition and -22.9 in the other: the threshold is
	// not robust.
	static final double LEVEL_BEFORE_DROPOUT_DB = -20.0;
	static final double MAX_DROPOUT_SECONDS = 5.0;

	public enum DefectType {
		DECODING_ERRORS,
		TRUNCATED,
		DROPOUTS,
		LINE_TONES
	}

	public record Defect(DefectType type, String description, double seconds) {

		public Defect {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(description, "description");
		}
	}

	private AudioIntegrity() {
	}

	/**
	 * @param file              for the format, from the extension
	 * @param recordingDuration duration of the recording identified by Tier 2, if known
	 */
	public static List<Defect> assess(Path file, AudioAnalysisPort.Analysis analysis, Optional<Integer> recordingDuration) {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(analysis, "analysis");
		Objects.requireNonNull(recordingDuration, "recordingDuration");
		String format = extension(file);
		boolean mp3 = format.equals("mp3");
		List<Defect> defects = new ArrayList<>();

		// FLAC and OGG have a CRC per frame or page: every error is real damage (none in the 38 files of
		// the library). For the other formats there is no calibration: the MP3 one is used.
		boolean withCrc = format.equals("flac") || format.equals("ogg");
		int threshold = withCrc ? 1 : MP3_ERRORS;
		if (analysis.decodingErrors() >= threshold) {
			// With many lost frames the gap from the declared duration says more than the line count
			// ("Chop Suey": 1509 frames counted, 199 s missing).
			double gap = analysis.declaredDurationSeconds()
			                     .map(d -> d - analysis.decodedDurationSeconds())
			                     .orElse(0.0);
			double lost = Math.max(mp3 ? analysis.decodingErrors() / 2 * SECONDS_PER_MP3_FRAME : 0.0, gap);
			defects.add(new Defect(DefectType.DECODING_ERRORS, analysis.decodingErrors() + " decoding errors" + (lost > 0 ? String.format(Locale.ROOT, ", about %.1f s of audio lost", lost) : ""), lost));
		}

		double decoded = analysis.decodedDurationSeconds();
		Optional<Defect> truncated = Optional.empty();
		if (recordingDuration.isPresent() && recordingDuration.get() - decoded > RECORDING_GAP_SECONDS) {
			double missing = recordingDuration.get() - decoded;
			truncated = Optional.of(new Defect(DefectType.TRUNCATED, String.format(Locale.ROOT, "truncated: %.0f s decoded against %d s of the identified recording", decoded, recordingDuration.get()), missing));
		} else if (analysis.declaredDurationSeconds().isPresent()) {
			double missing = analysis.declaredDurationSeconds().get() - decoded;
			// Truncated OGG and CBR MP3 files already declare the truncated duration: the gap does not see
			// them.
			boolean brokenEnding = format.equals("flac") || (mp3 && analysis.finalLevelDb() > TRUNCATED_FINAL_LEVEL_DB);
			if (missing > TRUNCATION_GAP_SECONDS && brokenEnding) {
				truncated = Optional.of(new Defect(DefectType.TRUNCATED, String.format(Locale.ROOT, "truncated: %.0f s decoded against %.0f s declared", decoded, analysis.declaredDurationSeconds()
				                                                                                                                                                          .get()), missing));
			}
		}
		truncated.ifPresent(defects::add);

		List<AudioAnalysisPort.Dropout> dropouts = analysis.dropouts()
		                                                   .stream()
		                                                   .filter(c -> c.durationSeconds() < MAX_DROPOUT_SECONDS && c.precedingLevelDb() > LEVEL_BEFORE_DROPOUT_DB)
		                                                   .toList();
		if (!dropouts.isEmpty()) {
			double total = dropouts.stream().mapToDouble(AudioAnalysisPort.Dropout::durationSeconds).sum();
			String where = String.join(", ", dropouts.stream().map(c -> minutes(c.startSeconds())).toList());
			defects.add(new Defect(DefectType.DROPOUTS, String.format(Locale.ROOT, "%d drops to digital silence (%.1f s in total) at %s", dropouts.size(), total, where), total));
		}
		// SIT tones: "Farther Away" by Evanescence, 8 sequences of about 1 s every 22 s; no other file of
		// the library has two different SIT tones within 1.5 s (calibration of 2026-10-04).
		if (!analysis.lineTones().isEmpty()) {
			String where = String.join(", ", analysis.lineTones().stream().map(AudioIntegrity::minutes).toList());
			defects.add(new Defect(DefectType.LINE_TONES, String.format(Locale.ROOT, "%d phone line tone (SIT) sequences at %s", analysis.lineTones()
			                                                                                                                             .size(), where), analysis.lineTones()
			                                                                                                                                                      .size() * SIT_SEQUENCE_SECONDS));
		}
		return defects;
	}

	// Duration of a SIT sequence: three tones of 0.27-0.38 s.
	private static final double SIT_SEQUENCE_SECONDS = 1.0;

	private static String minutes(double seconds) {
		int s = (int) Math.floor(seconds);
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	private static String extension(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}
}
