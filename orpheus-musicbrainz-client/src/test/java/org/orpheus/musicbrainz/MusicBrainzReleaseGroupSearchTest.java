package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.port.ReleaseGroupSearchPort;
import org.orpheus.domain.port.ReleaseGroupSearchPort.FoundReleaseGroup;

@QuarkusTest
@QuarkusTestResource(MusicBrainzApiTestResource.class)
class MusicBrainzReleaseGroupSearchTest {

	@Inject
	ReleaseGroupSearchPort sut;

	@Test
	void translatesTheCandidatesWithCreditAndTypes() {
		var candidates = sut.search("Deep Purple", "Burn").orElseThrow();

		assertThat(candidates).extracting(FoundReleaseGroup::id).containsExactly("rg-burn-ep", "rg-burn");
		assertThat(candidates.get(1).artistCredit()).isEqualTo("Deep Purple");
		assertThat(candidates.get(1).artistIds()).containsExactly("id-dp");
		assertThat(candidates.get(1).primaryType()).isEqualTo("Album");
		assertThat(MusicBrainzApiTestResource.lastQuery).contains("query=releasegroup:\"Burn\" AND artist:\"Deep Purple\"");
	}

	@Test
	void quotesInTheTitleAreEscaped() {
		assertThat(MusicBrainzReleaseGroupSearch.phrase("The \"best\" \\ of")).isEqualTo("The \\\"best\\\" \\\\ of");
	}
}
