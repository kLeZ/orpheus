package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrackMetadataTest {

	@Test
	void everyTaggableFieldCanBeAbsent() {
		var metadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.UNKNOWN);

		assertThat(metadata.artist()).isNull();
		assertThat(metadata.title()).isNull();
		assertThat(metadata.year()).isNull();
	}

	@Test
	void acceptsACorruptedYearWithoutRejectingIt() {
		var metadata = new TrackMetadata(
		        "", "Track 1", null, null, 0, -1, null, ExternalIds.UNKNOWN);

		assertThat(metadata.year()).isZero();
		assertThat(metadata.trackNumber()).isEqualTo(-1);
	}

	@Test
	void rejectsNullExternalIds() {
		assertThatThrownBy(
		        () -> new TrackMetadata(null, null, null, null, null, null, null, null))
		                                                                                .isInstanceOf(NullPointerException.class);
	}
}
