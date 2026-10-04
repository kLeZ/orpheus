package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.ingest.AudioIntegrity.DefectType;
import org.orpheus.domain.port.AudioAnalysisPort.Analysis;
import org.orpheus.domain.port.AudioAnalysisPort.Dropout;

// Measures of the real files of the calibration of 2026-10-04 (ADR-0023).
class AudioIntegrityTest {

	private static final Path MP3 = Path.of("x.mp3");

	@Test
	void lostMp3FramesFromTwentyUp() {
		// "Never Say Goodbye": 210 error lines, 105 frames
		var defects = AudioIntegrity.assess(MP3, new Analysis(Optional.of(289.9), 287.5, 210, List.of(), -35), Optional.empty());

		assertThat(defects).extracting(AudioIntegrity.Defect::type).containsExactly(DefectType.DECODING_ERRORS);
		assertThat(defects.get(0).seconds()).isBetween(2.7, 2.8);
	}

	@Test
	void anMp3FrameDiscardedAtTheEdgesIsNotADefect() {
		assertThat(AudioIntegrity.assess(MP3, new Analysis(Optional.of(200.0), 200.0, 2, List.of(), -15), Optional.empty())).isEmpty();
	}

	@Test
	void flacAndOggOneErrorIsEnough() {
		var analysis = new Analysis(Optional.of(60.0), 60.0, 3, List.of(), -10);

		assertThat(AudioIntegrity.assess(Path.of("x.flac"), analysis, Optional.empty())).hasSize(1);
		assertThat(AudioIntegrity.assess(Path.of("x.OGG"), analysis, Optional.empty())).hasSize(1);
	}

	@Test
	void aTruncatedMp3BreaksOffOneWithAWrongHeaderEndsInSilence() {
		// Nickelback "Figured You Out": 182 s out of 228, ending at -10.5 dBFS
		assertThat(AudioIntegrity.assess(MP3, new Analysis(Optional.of(228.5), 181.9, 0, List.of(), -10.5), Optional.empty()))
		                                                                                                                      .extracting(AudioIntegrity.Defect::type)
		                                                                                                                      .containsExactly(DefectType.TRUNCATED);
		// Sentenced "With Bitterness And Joy": intact, wrong header, ending at -27.9 dBFS
		assertThat(AudioIntegrity.assess(MP3, new Analysis(Optional.of(324.0), 284.0, 0, List.of(), -27.9), Optional.empty())).isEmpty();
	}

	@Test
	void aTruncatedFlacWithoutLookingAtTheEnding() {
		assertThat(AudioIntegrity.assess(Path.of("x.flac"), new Analysis(Optional.of(60.0), 35.6, 0, List.of(), -60), Optional.empty()))
		                                                                                                                                .extracting(AudioIntegrity.Defect::type)
		                                                                                                                                .containsExactly(DefectType.TRUNCATED);
	}

	@Test
	void aTruncatedOggShowsOnlyAgainstTheRecording() {
		var analysis = new Analysis(Optional.of(34.7), 34.7, 0, List.of(), -10);

		assertThat(AudioIntegrity.assess(Path.of("x.ogg"), analysis, Optional.empty())).isEmpty();
		assertThat(AudioIntegrity.assess(Path.of("x.ogg"), analysis, Optional.of(60))).extracting(AudioIntegrity.Defect::type)
		                                                                              .containsExactly(DefectType.TRUNCATED);
	}

	@Test
	void dropoutsOnlyIfCleanAndShortCuts() {
		// Evanescence "Farther Away": cuts from -15.7 dBFS; Green Day "Brain Stew": faded pauses
		List<Dropout> dropouts = List.of(new Dropout(83.8, 1.8, -15.7), new Dropout(105.4, 2.1, -19.9), new Dropout(7.1, 1.0, -54.2), new Dropout(40.0, 6.0, -10));

		var defects = AudioIntegrity.assess(MP3, new Analysis(Optional.of(241.0), 241.0, 0, dropouts, -40), Optional.empty());

		assertThat(defects).extracting(AudioIntegrity.Defect::type).containsExactly(DefectType.DROPOUTS);
		assertThat(defects.get(0).description()).contains("2 drops").contains("1:23").contains("1:45");
		assertThat(defects.get(0).seconds()).isCloseTo(3.9, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void sequencesOfPhoneLineTones() {
		var defects = AudioIntegrity.assess(MP3, new Analysis(Optional.of(241.0), 241.0, 0, List.of(), -40, List.of(59.77, 82.76)), Optional.empty());

		assertThat(defects).extracting(AudioIntegrity.Defect::type).containsExactly(DefectType.LINE_TONES);
		assertThat(defects.get(0).description()).contains("2 phone line tone").contains("0:59").contains("1:22");
	}
}
