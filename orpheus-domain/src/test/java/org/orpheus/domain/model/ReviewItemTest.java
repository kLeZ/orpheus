package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewItemTest {

	private static Track aTrack() {
		var metadata = new TrackMetadata(
		        null, "Track 1", null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/source/t1.mp3"), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/t1.mp3"), metadata, provenance);
	}

	@Test
	void anUncertainIdentificationItemCarriesItsSignal() {
		var item = new ReviewItem(
		        aTrack(), ReviewCategory.UNCERTAIN_IDENTIFICATION, "match at distance 0.15 from the tagged title", ReviewStatus.PENDING, List.of(new RawSignal.MatchDistance(0.15, 0.7)));

		assertThat(item.signals()).hasSize(1);
		assertThat(item.status()).isEqualTo(ReviewStatus.PENDING);
	}

	@Test
	void anUnidentifiedTrackHasNoSignal() {
		var item = new ReviewItem(
		        aTrack(), ReviewCategory.UNIDENTIFIED, "no match at any identification tier", ReviewStatus.PENDING, List.of());

		assertThat(item.signals()).isEmpty();
	}

	@Test
	void theSignalListIsImmutable() {
		var signals = new java.util.ArrayList<RawSignal>();
		signals.add(new RawSignal.FingerprintScore(0.95, 0.9));
		var item = new ReviewItem(
		        aTrack(), ReviewCategory.TIER2_RECONSTRUCTION, "identified by fingerprint", ReviewStatus.PENDING, signals);

		signals.add(new RawSignal.FingerprintScore(0.5, 0.5));

		assertThat(item.signals()).hasSize(1);
		assertThatThrownBy(
		        () -> item.signals().add(new RawSignal.FingerprintScore(0.1, 0.1)))
		                                                                           .isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsABlankReason() {
		assertThatThrownBy(
		        () -> new ReviewItem(
		                aTrack(), ReviewCategory.AUDIO_QUALITY, "", ReviewStatus.PENDING, List.of()))
		                                                                                             .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANullTrack() {
		assertThatThrownBy(
		        () -> new ReviewItem(
		                null, ReviewCategory.AUDIO_QUALITY, "reason", ReviewStatus.PENDING, List.of()))
		                                                                                               .isInstanceOf(NullPointerException.class);
	}
}
