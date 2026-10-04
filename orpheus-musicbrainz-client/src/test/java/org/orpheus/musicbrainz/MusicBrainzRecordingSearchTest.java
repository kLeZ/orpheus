package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.orpheus.musicbrainz.dto.ArtistCreditDto;
import org.orpheus.musicbrainz.dto.RecordingSearchDto;

class MusicBrainzRecordingSearchTest {

	@Test
	void durationInSecondsAndRebuiltCredit() {
		var dto = new RecordingSearchDto(List.of(new RecordingSearchDto.Entry("a", "Hey Jude", 431_333, List.of(new ArtistCreditDto("The Beatles", "", null))), new RecordingSearchDto.Entry("b", "Hey Jude", null, List.of())));

		var found = MusicBrainzRecordingSearch.translate(dto);

		assertThat(found).hasSize(2);
		assertThat(found.get(0).durationSeconds()).hasValue(431);
		assertThat(found.get(0).artistCredit()).isEqualTo("The Beatles");
		assertThat(found.get(1).durationSeconds()).isEmpty();
		assertThat(MusicBrainzRecordingSearch.translate(null)).isEmpty();
	}
}
