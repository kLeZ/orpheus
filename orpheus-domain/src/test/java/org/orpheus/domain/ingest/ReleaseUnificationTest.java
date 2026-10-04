package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;

// "Ecliptica" by Sonata Arctica on the real run: 10 tracks on the 1999 release, 2 on the 2003 reissue.
class ReleaseUnificationTest {

	private static Release e(String id, int year, int number) {
		return new Release(id, ReleaseType.ALBUM, ReleaseDate.yearOnly(year), List.of("Sonata Arctica"), false, new ReleaseInfo("Ecliptica", "Sonata Arctica", "rec" + number, 1, number, "T" + number));
	}

	@Test
	void theTracksOfTheMinorityReleaseTakeYearAndTitleOfTheDominantOne() {
		Release a1 = e("1999", 1999, 1);
		Release a2 = e("1999", 1999, 2);
		Release b = e("2003", 2003, 3);

		ReleaseUnification u = ReleaseUnification.of(List.of(a1, a2, b));

		Release unified = u.unified(b);
		assertThat(unified.releaseDate().year()).isEqualTo(1999);
		assertThat(unified.releaseId()).isEqualTo("1999");
		assertThat(unified.info().number()).isEqualTo(3);
		assertThat(unified.info().disc()).isNull();
		assertThat(u.yearFor("Sonata Arctica", "Ecliptica", List.of("T2"))).contains(1999);
		assertThat(u.yearFor("Sonata Arctica", "Ecliptica", List.of("Another song"))).isEmpty();
		assertThat(u.unified(a1)).isSameAs(a1);
	}

	@Test
	void differentAlbumsAreNotUnified() {
		Release a = e("1999", 1999, 1);
		Release other = new Release("silence", ReleaseType.ALBUM, ReleaseDate.yearOnly(2001), List.of("Sonata Arctica"), false, new ReleaseInfo("Silence", "Sonata Arctica", "r", 1, 1, "X"));

		ReleaseUnification u = ReleaseUnification.of(List.of(a, other));

		assertThat(u.unified(other)).isSameAs(other);
	}

	// "Piece of Mind": Tier 2 tracks on 1983, tracks with tag 1984, no song in common; the tracklist of
	// the release says they are the same record.
	@Test
	void aTier1AlbumWithoutSongsInCommonIsUnifiedIfTheSongIsInTheTracklist() {
		Release pom = new Release("pom", ReleaseType.ALBUM, ReleaseDate.yearOnly(1983), List.of("Iron Maiden"), false, new ReleaseInfo("Piece of Mind", "Iron Maiden", "r", 1, 1, "Where Eagles Dare"));
		ReleaseUnification u = ReleaseUnification.of(List.of(pom));

		assertThat(u.dominantFor("Iron Maiden", "Piece Of Mind", List.of("Flight of Icarus"), r -> List.of("Where Eagles Dare", "Flight of Icarus"))).contains(pom);
		assertThat(u.dominantFor("Iron Maiden", "Piece Of Mind", List.of("Flight of Icarus"), r -> List.of())).isEmpty();
	}

	// "Weezer" of 1994 and of 2001: same name, different songs.
	@Test
	void twoSameNamedAlbumsWithDifferentSongsStayDistinct() {
		Release blue = new Release("blue", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994), List.of("Weezer"), false, new ReleaseInfo("Weezer", "Weezer", "r", 1, 4, "Buddy Holly"));
		ReleaseUnification u = ReleaseUnification.of(List.of(blue));

		assertThat(u.dominantFor("Weezer", "Weezer", List.of("Hash Pipe"), r -> List.of("My Name Is Jonas", "Buddy Holly", "Say It Ain't So"))).isEmpty();
	}
}
