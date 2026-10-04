package org.orpheus.domain.identification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.RecordingSearchPort.FoundRecording;

/**
 * Durations to repeat the AcoustID lookup with, for a file that finds nothing with its own (ADR-0023,
 * intervention 4). AcoustID filters by duration with a window of about 7 s: a truncated MP3 ("Hey
 * Jude" by the Beatles, 380 s out of 431) is recognized only with the duration of the whole song, taken
 * from the MusicBrainz recordings with the same title and the same artist.
 */
public final class DurationsByName {

	static final int MIN_GAP_SECONDS = 7;
	static final int MAX_ATTEMPTS = 3;

	public record Names(String artist, String title) {
	}

	private DurationsByName() {
	}

	/** Artist (from the tag or the folder) and title of the track, if both known. */
	public static Optional<Names> names(Track track) {
		String artist = track.metadata().artist();
		if (Placeholder.missingArtist(artist)) {
			artist = track.artistFromPath();
		}
		String title = track.metadata().title();
		if (artist == null || Placeholder.missingArtist(artist) || title == null || key(title).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Names(artist.strip(), title.strip()));
	}

	/**
	 * The durations of the recordings with an equal title and a matching artist, differing from the one
	 * already tried by at least {@link #MIN_GAP_SECONDS}, most frequent first, at most
	 * {@link #MAX_ATTEMPTS}.
	 */
	public static List<Integer> candidates(Names names, int triedDuration, List<FoundRecording> found) {
		Objects.requireNonNull(names, "names");
		Objects.requireNonNull(found, "found");
		String title = key(names.title());
		List<Integer> durations = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (FoundRecording r : found) {
			if (r.durationSeconds().isEmpty() || !key(r.title()).equals(title) || !ArtistComparison.matches(names
			                                                                                                     .artist(), List.of(r.artistCredit()))) {
				continue;
			}
			int d = r.durationSeconds().get();
			if (Math.abs(d - triedDuration) < MIN_GAP_SECONDS) {
				continue;
			}
			int similar = -1;
			for (int i = 0; i < durations.size(); i++) {
				if (Math.abs(durations.get(i) - d) < MIN_GAP_SECONDS) {
					similar = i;
					break;
				}
			}
			if (similar >= 0) {
				counts.set(similar, counts.get(similar) + 1);
			} else {
				durations.add(d);
				counts.add(1);
			}
		}
		List<Integer> indexes = new ArrayList<>();
		for (int i = 0; i < durations.size(); i++) {
			indexes.add(i);
		}
		indexes.sort((a, b) -> Integer.compare(counts.get(b), counts.get(a)));
		return indexes.stream().limit(MAX_ATTEMPTS).map(durations::get).toList();
	}

	private static String key(@Nullable String s) {
		if (s == null) {
			return "";
		}
		StringBuilder k = new StringBuilder();
		java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFKD)
		                    .codePoints()
		                    .filter(Character::isLetterOrDigit)
		                    .forEach(k::appendCodePoint);
		return k.toString();
	}
}
