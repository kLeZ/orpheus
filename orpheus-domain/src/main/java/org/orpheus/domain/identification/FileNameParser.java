package org.orpheus.domain.identification;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.ArtistComparison;

/**
 * Reading of number, artist and title from the name of an audio file (corrective plan, phase 1). Real
 * forms seen in {@code ~/Musica}: "05 - Crawling", "06. Enlarge (Your penis)", " - Numb" (number lost,
 * the dash remains), "Ray Charles - Amazing Grace", "Myr - Days Of Convergence - 01 - Apprentice".
 * Without a known artist, "X - Y" is ambiguous: X is proposed as the artist. With a known artist, X is
 * the artist only if it resembles it; otherwise everything is title ("Lagwagon/ - Take a look around -
 * Punk cover" is a title, not the artist "Take a look around").
 */
public final class FileNameParser {

	public record Result(Optional<Integer> number, Optional<String> artist, Optional<String> title) {

		static Result empty() {
			return new Result(Optional.empty(), Optional.empty(), Optional.empty());
		}
	}

	// "Artist - Album - 01 - Title" or "Artist - 01 - Title": the number is in the middle.
	private static final Pattern NUMBER_IN_THE_MIDDLE = Pattern.compile("^(.+?) - (\\d{1,3}) - (.+)$");
	// "05 - Title", "06. Title", "01-Title": at most 3 digits, so "1979" stays a title. With a space
	// alone as separator a leading zero is required ("02 What'd I Say"): "21 Guns" and "2 Minutes to
	// Midnight" are titles.
	private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d{1,3})\\s*[-.:)]\\s*(.+)$|^(0\\d)\\s+(.+)$");
	private static final Pattern LEADING_DASH = Pattern.compile("^[-\\s]+");
	private static final String SEPARATOR = " - ";

	private FileNameParser() {
	}

	public static Result parse(String fileName, @Nullable String knownArtist) {
		String s = LEADING_DASH.matcher(withoutExtension(fileName).strip()).replaceFirst("").strip();
		if (s.isEmpty()) {
			return Result.empty();
		}

		Matcher inTheMiddle = NUMBER_IN_THE_MIDDLE.matcher(s);
		if (inTheMiddle.matches()) {
			String artist = inTheMiddle.group(1).split(SEPARATOR)[0].strip();
			return new Result(Optional.of(Integer.valueOf(inTheMiddle.group(2))), Optional.of(artist), title(inTheMiddle.group(3)));
		}

		Optional<Integer> number = Optional.empty();
		Matcher leading = LEADING_NUMBER.matcher(s);
		if (leading.matches()) {
			boolean withSeparator = leading.group(1) != null;
			number = Optional.of(Integer.valueOf(withSeparator ? leading.group(1) : leading.group(3)));
			s = LEADING_DASH.matcher(withSeparator ? leading.group(2) : leading.group(4))
			                .replaceFirst("")
			                .strip();
		}

		int separator = s.indexOf(SEPARATOR);
		if (separator < 0) {
			return new Result(number, Optional.empty(), title(s));
		}
		String before = s.substring(0, separator).strip();
		String rest = s.substring(separator + SEPARATOR.length()).strip();
		if (knownArtist == null || knownArtist.isBlank()) {
			return new Result(number, Optional.of(before).filter(p -> !p.isEmpty()), title(rest));
		}
		if (ArtistComparison.matches(before, List.of(knownArtist))) {
			return new Result(number, Optional.of(before), title(rest));
		}
		return new Result(number, Optional.empty(), title(s));
	}

	private static Optional<String> title(String s) {
		String t = s.strip();
		return t.isEmpty() ? Optional.empty() : Optional.of(t);
	}

	private static String withoutExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}
}
