package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.ingest.CopyPlacement.PlacementType;

// Cases from the real run (ADR-0019 rewritten on 2026-09-27).
class CopyPlacementTest {

	@Test
	void identicalAudioGetsPlusNNDifferentAudioVersionNN() {
		List<Path> paths = List.of(Path.of("Deep Purple/1974 - Burn/01 - Burn.mp3"), Path.of("Deep Purple/1974 - Burn/01 - Burn.mp3"), Path.of("Deep Purple/1974 - Burn/01 - Burn.mp3"), Path.of("Deep Purple/1974 - Burn/01 - Burn.mp3"));
		// 0 and 1 identical, 2 different from both, 3 identical to 2
		Map<Integer, String> audio = Map.of(0, "a", 1, "a", 2, "b", 3, "b");

		var c = CopyPlacement.place(paths, (i, j) -> audio.get(i).equals(audio.get(j)));

		assertThat(c).extracting(x -> x.relativePath().getFileName().toString())
		             .containsExactly("01 - Burn.mp3", "01 - Burn (2).mp3", "01 - Burn (version 2).mp3", "01 - Burn (3).mp3");
		assertThat(c).extracting(CopyPlacement.Placement::type)
		             .containsExactly(PlacementType.ORIGINAL, PlacementType.IDENTICAL_DUPLICATE, PlacementType.ALTERNATIVE_VERSION, PlacementType.IDENTICAL_DUPLICATE);
		assertThat(c.get(3).reference()).isEqualTo(2);
	}

	@Test
	void differentFormatsDoNotCollideAndWithoutCollisionsNothingIsCompared() {
		List<Path> paths = List.of(Path.of("Myr/2013 - Days Of Convergence/01 - Apprentice.flac"), Path.of("Myr/2013 - Days Of Convergence/01 - Apprentice.mp3"), Path.of("Myr/2013 - Days Of Convergence/01 - Apprentice.ogg"));

		var c = CopyPlacement.place(paths, (i, j) -> {
			throw new AssertionError("no comparison expected");
		});

		assertThat(c).extracting(CopyPlacement.Placement::relativePath).containsExactlyElementsOf(paths);
		assertThat(c).extracting(CopyPlacement.Placement::type).containsOnly(PlacementType.ORIGINAL);
	}

	@Test
	void aSuffixAlreadyTakenByAnotherFileIsSkipped() {
		List<Path> paths = List.of(Path.of("A/X/01 - T.mp3"), Path.of("A/X/01 - T (2).mp3"), Path.of("A/X/01 - T.mp3"));

		var c = CopyPlacement.place(paths, (i, j) -> true);

		assertThat(c.get(2).relativePath()).isEqualTo(Path.of("A/X/01 - T (3).mp3"));
	}

	@Test
	void theIntactCopyKeepsTheNameEvenIfItComesLater() {
		Path p = Path.of("Bon Jovi/1986 - Slippery When Wet/10 - Never Say Goodbye.mp3");
		List<Path> paths = List.of(p, p, p);

		// 0 damaged, 1 and 2 intact with different audio
		var c = CopyPlacement.place(paths, (i, j) -> false, "version", i -> i == 0);

		assertThat(c).extracting(x -> x.relativePath().getFileName().toString())
		             .containsExactly("10 - Never Say Goodbye (version 2).mp3", "10 - Never Say Goodbye.mp3", "10 - Never Say Goodbye (version 3).mp3");
		assertThat(c.get(1).type()).isEqualTo(PlacementType.ORIGINAL);
		assertThat(c.get(0).reference()).isEqualTo(1);
	}

	@Test
	void allDamagedTheOrderStays() {
		Path p = Path.of("A/01 - X.mp3");

		var c = CopyPlacement.place(List.of(p, p), (i, j) -> false, "version", i -> true);

		assertThat(c.get(0).type()).isEqualTo(PlacementType.ORIGINAL);
		assertThat(c.get(1).relativePath().getFileName().toString()).isEqualTo("01 - X (version 2).mp3");
	}
}
