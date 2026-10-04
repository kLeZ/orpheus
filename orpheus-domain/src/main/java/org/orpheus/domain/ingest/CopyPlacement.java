package org.orpheus.domain.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/**
 * Destination names when several source files would reach the same path (ADR-0019, rewritten on
 * 2026-09-27): every file always goes into the library. The first keeps the name; from the second on,
 * " (NN)" if the audio is identical to one of the files already placed on that path (a true duplicate,
 * like the Deep Purple albums present in two folders), " (version NN)" if the audio differs. The two
 * counters are separate and start from 2. The collision key includes the extension: the same track in
 * FLAC, MP3 and OGG already has different names and does not collide. The name without a suffix goes
 * to the first intact file of the group, if there is one (ADR-0023): a damaged copy does not take the
 * name away from the good one.
 */
public final class CopyPlacement {

	public enum PlacementType {
		ORIGINAL,
		IDENTICAL_DUPLICATE,
		ALTERNATIVE_VERSION
	}

	/**
	 * @param reference index of the first file placed on the same path (for a duplicate, the one with
	 *                  the same audio); equal to the index itself for an original
	 */
	public record Placement(Path relativePath, PlacementType type, int reference) {
	}

	private CopyPlacement() {
	}

	/**
	 * @param paths     relative path computed for each file, in processing order
	 * @param sameAudio true if the files of two indexes have the same decoded audio; called only for
	 *                  colliding files
	 */
	public static List<Placement> place(List<Path> paths, BiPredicate<Integer, Integer> sameAudio) {
		return place(paths, sameAudio, PlaceholderNames.DEFAULTS.version());
	}

	/** As above, with the configured word for alternative versions ("version" by default). */
	public static List<Placement> place(List<Path> paths, BiPredicate<Integer, Integer> sameAudio, String versionWord) {
		return place(paths, sameAudio, versionWord, i -> false);
	}

	/**
	 * @param damaged true for the files with audio defects ({@link AudioIntegrity}), which give up the
	 *                name without a suffix to an intact file of the same group
	 */
	public static List<Placement> place(List<Path> paths, BiPredicate<Integer, Integer> sameAudio, String versionWord, IntPredicate damaged) {
		Objects.requireNonNull(paths, "paths");
		Objects.requireNonNull(sameAudio, "sameAudio");
		Objects.requireNonNull(damaged, "damaged");
		Map<String, List<Integer>> byKey = new LinkedHashMap<>();
		Set<String> taken = new HashSet<>();
		for (int i = 0; i < paths.size(); i++) {
			String key = key(paths.get(i));
			byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
			taken.add(key);
		}
		List<Placement> result = new ArrayList<>(paths.size());
		for (int i = 0; i < paths.size(); i++) {
			result.add(null);
		}
		for (List<Integer> groupInOrder : byKey.values()) {
			List<Integer> group = new ArrayList<>(groupInOrder);
			group.stream().filter(i -> !damaged.test(i)).findFirst().ifPresent(intact -> {
				group.remove(intact);
				group.add(0, intact);
			});
			int first = group.get(0);
			result.set(first, new Placement(paths.get(first), PlacementType.ORIGINAL, first));
			int duplicates = 1;
			int versions = 1;
			List<Integer> placed = new ArrayList<>(List.of(first));
			for (int k = 1; k < group.size(); k++) {
				int i = group.get(k);
				Integer same = placed.stream().filter(j -> sameAudio.test(i, j)).findFirst().orElse(null);
				Path name;
				if (same != null) {
					do {
						name = withSuffix(paths.get(i), "(" + ++duplicates + ")");
					} while (!taken.add(key(name)));
					result.set(i, new Placement(name, PlacementType.IDENTICAL_DUPLICATE, same));
				} else {
					do {
						name = withSuffix(paths.get(i), "(" + versionWord + " " + ++versions + ")");
					} while (!taken.add(key(name)));
					result.set(i, new Placement(name, PlacementType.ALTERNATIVE_VERSION, first));
				}
				placed.add(i);
			}
		}
		return result;
	}

	private static Path withSuffix(Path path, String suffix) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String body = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		return path.resolveSibling(body + " " + suffix + extension);
	}

	private static String key(Path path) {
		return path.toString().toLowerCase(Locale.ROOT);
	}
}
