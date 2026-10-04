package org.orpheus.domain.identification;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.orpheus.domain.model.Track;

/**
 * Deterministic Tier 3a text heuristic (ADR-0017): reads the file name of the track looking for the
 * common patterns "Artist - Title" or "NN - Title" (after removing the leading track number). It never
 * produces an invented confidence: if the pattern is not recognizable, it returns
 * {@link Optional#empty()}.
 *
 * <p>This is the algorithmic line before a possible Tier 3b LLM (DESIGN.md). The heuristic is
 * conservative on purpose: it prefers returning nothing to a suggestion born of overly aggressive
 * parsing.
 */
public final class FileNameHeuristic {

	private FileNameHeuristic() {
	}

	// A suggestion for the review queue, so deliberately conservative: a title alone is proposed only
	// for the " - Title" form (number lost, the dash remains); "03 - Title" or a bare title are not.
	private static final Pattern LEADING_DASH = Pattern.compile("^\\s*-");
	private static final Pattern ARTIST_TITLE_WITHOUT_SPACES = Pattern.compile("^([^-\\s][^-]*)-([^-].*)$");

	public static Optional<TextClassification> classify(Track track) {
		String fileName = track.provenance().source().getFileName().toString();
		String knownArtist = track.metadata().artist() != null ? track.metadata().artist() : track.artistFromPath();
		FileNameParser.Result name = FileNameParser.parse(fileName, knownArtist);
		if (name.title().isEmpty()) {
			return Optional.empty();
		}
		if (name.artist().isPresent()) {
			return Optional.of(new TextClassification(name.artist(), Optional.empty(), name.title(), 0.8));
		}
		if (knownArtist == null) {
			Matcher withoutSpaces = ARTIST_TITLE_WITHOUT_SPACES.matcher(name.title().get());
			if (withoutSpaces.matches()) {
				return Optional.of(new TextClassification(Optional.of(withoutSpaces.group(1).strip()), Optional
				                                                                                               .empty(), Optional.of(withoutSpaces.group(2)
				                                                                                                                                  .strip()), 0.8));
			}
		}
		if (LEADING_DASH.matcher(fileName).find()) {
			return Optional.of(new TextClassification(Optional.empty(), Optional.empty(), name.title(), 0.5));
		}
		return Optional.empty();
	}
}
