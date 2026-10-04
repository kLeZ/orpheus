package org.orpheus.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.port.ReleaseLookupPort;

/**
 * Checks the real adapter (REST client and parsing) against a fake HTTP server
 * ({@link MusicBrainzApiTestResource}) serving canonical JSON per fixed recording id, instead of mocking
 * {@link MusicBrainzRestClient}: so the test also covers the Jackson deserialization of the DTOs, not
 * only the mapping logic.
 */
@QuarkusTest
@QuarkusTestResource(MusicBrainzApiTestResource.class)
class MusicBrainzReleaseLookupTest {

	@Inject
	ReleaseLookupPort sut;

	@Test
	void aReleaseWithOnlyAYearBecomesAYearOnlyReleaseDate() {
		List<Release> releases = sut.releasesFor("year-only");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info").containsExactly(
		        new Release("release-year-only", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994)));
	}

	@Test
	void aReleaseWithAFullDateBecomesAnExactReleaseDate() {
		List<Release> releases = sut.releasesFor("full-date");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info").containsExactly(
		        new Release(
		                "release-full-date", ReleaseType.ALBUM, ReleaseDate.exact(1994, 3, 15)));
	}

	@Test
	void aReleaseWithYearAndMonthIsDowngradedToYearOnlyWithoutInventingADay() {
		List<Release> releases = sut.releasesFor("year-month");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info").containsExactly(
		        new Release("release-year-month", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994)));
		assertThat(releases.get(0).releaseDate().precise()).isFalse();
	}

	@Test
	void aReleaseWithoutADateFieldIsDiscarded() {
		List<Release> releases = sut.releasesFor("no-date");

		assertThat(releases).isEmpty();
	}

	@Test
	void aMissingPrimaryTypeBecomesOther() {
		List<Release> releases = sut.releasesFor("type-missing");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info").containsExactly(
		        new Release("release-type-missing", ReleaseType.OTHER, ReleaseDate.yearOnly(2001)));
	}

	@Test
	void primaryTypeSingleBecomesSingle() {
		List<Release> releases = sut.releasesFor("type-single");

		assertThat(releases).extracting(Release::type).containsExactly(ReleaseType.SINGLE);
	}

	@Test
	void primaryTypeEpBecomesEp() {
		List<Release> releases = sut.releasesFor("type-ep");

		assertThat(releases).extracting(Release::type).containsExactly(ReleaseType.EP);
	}

	@Test
	void primaryTypeAlbumBecomesAlbum() {
		List<Release> releases = sut.releasesFor("type-album");

		assertThat(releases).extracting(Release::type).containsExactly(ReleaseType.ALBUM);
	}

	@Test
	void severalReleasesInTheSameResponseAreAllMappedAndTheOneWithoutDateDiscarded() {
		List<Release> releases = sut.releasesFor("several-releases");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info")
		                    .containsExactlyInAnyOrder(
		                            new Release("release-1", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994)), new Release("release-2", ReleaseType.SINGLE, ReleaseDate.exact(1994, 3, 15)));
	}

	@Test
	void releaseArtistAndCompilationSecondaryTypeAreMapped() {
		List<Release> releases = sut.releasesFor("compilation-va");

		assertThat(releases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("info").containsExactly(
		        new Release("release-compilation", ReleaseType.ALBUM, ReleaseDate.yearOnly(2000), List.of("Various Artists"), true), new Release("release-original", ReleaseType.ALBUM, ReleaseDate
		                                                                                                                                                                                           .exact(2001, 10, 2), List.of("Queen", "David Bowie"), false));
	}

	@Test
	void demoLiveAndBootlegAreSecondarySoundtrackIsNot() {
		List<Release> releases = sut.releasesFor("secondary");

		assertThat(releases).extracting(Release::releaseId, Release::secondary)
		                    .containsExactly(
		                            tuple("release-demo", true), tuple("release-live", true), tuple("release-bootleg", true), tuple("release-soundtrack", false));
	}

	@Test
	void titleCreditDiscAndTrackPositionAreMapped() {
		List<Release> releases = sut.releasesFor("with-media");

		assertThat(releases).hasSize(1);
		ReleaseInfo info = releases.get(0).info();
		assertThat(info).isNotNull();
		assertThat(info.title()).isEqualTo("Hot Space");
		assertThat(info.artistCredit()).isEqualTo("Queen & David Bowie");
		assertThat(info.recordingId()).isEqualTo("with-media");
		assertThat(info.disc()).isEqualTo(2);
		assertThat(info.number()).isEqualTo(11);
		assertThat(info.trackTitle()).isEqualTo("Under Pressure");
		assertThat(info.releaseGroupId()).isEqualTo("rg-hot-space");
		assertThat(info.trackArtistCredit()).isEqualTo("Queen & David Bowie");
		assertThat(info.trackArtistIds()).containsExactly("id-queen", "id-bowie");
		assertThat(info.releaseArtistIds()).containsExactly("id-queen", "id-bowie");
	}

	@Test
	void anUnknownRecordingIdDoesNotPropagateAnExceptionAndReturnsAnEmptyList() {
		List<Release> releases = sut.releasesFor("recording-that-does-not-exist");

		assertThat(releases).isEmpty();
	}
}
