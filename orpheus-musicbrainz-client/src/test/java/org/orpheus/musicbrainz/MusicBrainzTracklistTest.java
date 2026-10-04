package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.orpheus.musicbrainz.dto.ArtistDto;
import org.orpheus.musicbrainz.dto.RecordingRelationsDto;
import org.orpheus.musicbrainz.dto.RecordingRelationsDto.Work;
import org.orpheus.musicbrainz.dto.RecordingRelationsDto.Relation;

// Shape of the responses checked with a real call on 2026-10-04 ("Sliver" by Nirvana).
class MusicBrainzTracklistTest {

	@Test
	void authorsFromComposerAndWriterInMusicBrainzOrderWithoutLyricistOrDuplicates() {
		Work sliver = new Work("Sliver", List.of(new Relation("composer", null, new ArtistDto("1", "Chad Channing")), new Relation("composer", null, new ArtistDto("2", "Kurt Cobain")), new Relation("composer", null, new ArtistDto("3", "Krist Novoselic")), new Relation("lyricist", null, new ArtistDto("2", "Kurt Cobain")), new Relation("writer", null, new ArtistDto("2", "Kurt Cobain"))));
		RecordingRelationsDto dto = new RecordingRelationsDto(List.of(new Relation("producer", null, new ArtistDto("9", "Jack Endino")), new Relation("performance", sliver, null)));

		assertThat(MusicBrainzTracklist.composersOf(dto)).containsExactly("Chad Channing", "Kurt Cobain", "Krist Novoselic");
		assertThat(MusicBrainzTracklist.composersOf(null)).isEmpty();
	}
}
