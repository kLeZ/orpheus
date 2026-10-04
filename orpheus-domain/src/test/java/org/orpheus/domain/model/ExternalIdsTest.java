package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalIdsTest {

	@Test
	void unknownHasEveryFieldNull() {
		var ids = ExternalIds.unknown();

		assertThat(ids.musicBrainzRecordingId()).isNull();
		assertThat(ids.musicBrainzReleaseId()).isNull();
		assertThat(ids.acoustId()).isNull();
	}

	@Test
	void canCarryOnlySomeKnownIds() {
		var ids = new ExternalIds("rec-1", null, null);

		assertThat(ids.musicBrainzRecordingId()).isEqualTo("rec-1");
		assertThat(ids.musicBrainzReleaseId()).isNull();
	}
}
