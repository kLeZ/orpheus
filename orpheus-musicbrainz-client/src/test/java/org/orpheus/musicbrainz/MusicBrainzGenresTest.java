package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.port.GenresPort;

@QuarkusTest
@QuarkusTestResource(MusicBrainzApiTestResource.class)
class MusicBrainzGenresTest {

	@Inject
	GenresPort sut;

	@Test
	void genresByVotesAndAlphabeticalOnATie() {
		assertThat(sut.releaseGroupGenres("rg-hot-space")).hasValueSatisfying(g -> assertThat(g).containsExactly("funk", "disco", "rock"));
	}

	@Test
	void noGenresAndAnUnknownIdAreValidEmptyAnswers() {
		assertThat(sut.artistGenres("id-queen")).hasValueSatisfying(g -> assertThat(g).isEmpty());
		assertThat(sut.artistGenres("id-missing")).hasValueSatisfying(g -> assertThat(g).isEmpty());
	}
}
