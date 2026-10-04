package org.orpheus.domain.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Measures on the decoded audio of a file, to recognize damaged files (ADR-0023): duration declared
 * by the container and duration actually decoded, decoder error lines, drops to digital silence and
 * level of the ending.
 */
public interface AudioAnalysisPort {

	/**
	 * @param startSeconds     start of the dropout
	 * @param durationSeconds  duration of the digital silence
	 * @param precedingLevelDb RMS level of the 20 ms before the dropout, in dBFS
	 */
	record Dropout(double startSeconds, double durationSeconds, double precedingLevelDb) {
	}

	/**
	 * @param declaredDurationSeconds duration according to the container or the header, if present
	 * @param decodedDurationSeconds  duration of the samples obtained by decoding
	 * @param decodingErrors          decoder error lines
	 * @param dropouts                digital silences inside the song, at least 0.25 s, more than 2 s
	 *                                from start and end
	 * @param finalLevelDb            RMS level of the last 100 ms, in dBFS
	 * @param lineTones               start, in seconds, of every SIT tone sequence of the telephone
	 *                                network (the error message of a call, ended up in downloads of
	 *                                the 56k era)
	 */
	record Analysis(Optional<Double> declaredDurationSeconds,
	                double decodedDurationSeconds,
	                int decodingErrors,
	                List<Dropout> dropouts,
	                double finalLevelDb,
	                List<Double> lineTones) {

		public Analysis {
			Objects.requireNonNull(declaredDurationSeconds, "declaredDurationSeconds");
			Objects.requireNonNull(dropouts, "dropouts");
			dropouts = List.copyOf(dropouts);
			lineTones = lineTones == null ? List.of() : List.copyOf(lineTones);
		}

		public Analysis(Optional<Double> declaredDurationSeconds, double decodedDurationSeconds, int decodingErrors, List<Dropout> dropouts, double finalLevelDb) {
			this(declaredDurationSeconds, decodedDurationSeconds, decodingErrors, dropouts, finalLevelDb, List.of());
		}
	}

	/** Empty if the file cannot be decoded or the decoder is not available. */
	Optional<Analysis> analyze(Path file);
}
