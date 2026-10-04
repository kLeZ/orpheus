package org.orpheus.domain.identification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.matching.NormalizedLevenshtein;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.TextClassificationPort;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Orchestrator of the whole 0, 1, 2, 3 identification cascade (ADR-0017).
 *
 * <p>Pure domain logic: takes tracks and ports and produces structured results per track, testable
 * without the Quarkus runtime. The order is the one of SPEC.md 4.4: local Tier 0/1, multi-track
 * consensus on the eligible groups, individual Tier 2 on the residues, Tier 3 text classification on
 * the last residues. No I/O or file copy: those stay in the CLI command.
 */
public final class IdentificationPipeline {

	private IdentificationPipeline() {
	}

	public static List<TrackOutcome> run(
	                                     List<Track> tracks, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup, TextClassificationPort textClassification) {
		return run(tracks, null, fingerprintLookup, releaseLookup, textClassification);
	}

	/**
	 * @param root root of the source: enables the evidence from the path ({@link LocalEvidence}) and the
	 *             rule "the root is not an album" of the Tier 0/1 engine; if null, the metadata is still
	 *             cleaned but without evidence tied to the root
	 */
	public static List<TrackOutcome> run(
	                                     List<Track> readTracks, @Nullable Path root, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup, TextClassificationPort textClassification) {
		Objects.requireNonNull(readTracks, "tracks");
		Objects.requireNonNull(fingerprintLookup, "fingerprintLookup");
		Objects.requireNonNull(releaseLookup, "releaseLookup");

		List<Track> tracks = LocalEvidence.apply(readTracks, root);
		LocalIdentificationResult local = LocalIdentifier.identify(tracks, root);
		List<TrackOutcome> outcomes = new ArrayList<>();

		for (Album localAlbum : local.albums()) {
			Album album = withYearFromRelease(localAlbum, fingerprintLookup, releaseLookup);
			if (MultiTrackConsensus.isEligibleGroup(album)) {
				ConsensusResult consensus = MultiTrackConsensus.compute(album, fingerprintLookup, releaseLookup);
				for (ConsensusTrackOutcome cto : consensus.tracks()) {
					Optional<Release> release = cto.assignedRelease();
					Optional<TextClassification> tier3 = tier3Suggestion(cto.track(), release, textClassification);
					outcomes.add(new TrackOutcome(cto.track(), album, release, cto.forced(), tier3, Optional.empty(), cto.recording()));
				}
			} else {
				for (Track albumTrack : album.tracks()) {
					Track t = withNumberFromRelease(albumTrack, album, fingerprintLookup, releaseLookup);
					Optional<Release> release = Optional.empty();
					Optional<String> contrast = Optional.empty();
					Optional<CandidateRecording> recording = Optional.empty();
					if (LocalIdentifier.placeholderOrEmptyAlbumTag(t) || unverifiableAlbum(album)) {
						Tier2Candidacy candidacy = Tier2Identifier.identify(t, fingerprintLookup, releaseLookup);
						release = candidacy.selectedRelease();
						contrast = contrastIfApplied(candidacy);
						// A tagged album needs a fingerprint agreeing with the artist and an ordinary release:
						// "Imagine" credited to the Beatles exists only on a bootleg and stays on the album of
						// the tag.
						if (!LocalIdentifier.placeholderOrEmptyAlbumTag(t) && (contrast.isPresent() || release.map(Release::secondary)
						                                                                                      .orElse(false))) {
							release = Optional.empty();
							contrast = Optional.empty();
						}
						recording = release.flatMap(candidacy::recordingOf);
					}
					Optional<TextClassification> tier3 = tier3Suggestion(t, release, textClassification);
					outcomes.add(new TrackOutcome(t, album, release, false, tier3, contrast, recording));
				}
			}
		}

		for (Track t : local.isolatedTracks()) {
			Tier2Candidacy candidacy = Tier2Identifier.identify(t, fingerprintLookup, releaseLookup);
			Optional<Release> release = candidacy.selectedRelease();
			Optional<TextClassification> tier3 = tier3Suggestion(t, release, textClassification);
			outcomes.add(new TrackOutcome(t, null, release, false, tier3, contrastIfApplied(candidacy), release.flatMap(candidacy::recordingOf)));
		}

		return outcomes;
	}

	/**
	 * Track number for a track of a tagged album that does not carry one (120 tracks on the run of
	 * 2026-10-04, all 13 of "Ocean Avenue" by Yellowcard): the most frequent position in the Tier 2
	 * releases with the album title. Without matching releases the track stays as it is.
	 */
	static Track withNumberFromRelease(Track t, Album album, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup) {
		TrackMetadata m = t.metadata();
		// A title made only of a number ("Black Sabbath/Sabotage/ - 03.mp3") is probably the track
		// number, but "36" by System Of A Down is a real title: only a non-numeric title at the same
		// position in the release replaces it.
		boolean withoutTitle = Placeholder.missingTitle(m.title()) || numberOnly(m.title());
		if ((m.trackNumber() != null && !withoutTitle) || album.title() == null || LocalIdentifier.placeholderOrEmptyAlbumTag(t)) {
			return t;
		}
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		java.util.Map<String, ReleaseInfo> byKey = new java.util.HashMap<>();
		for (Release e : Tier2Identifier.identify(t, fingerprintLookup, releaseLookup).candidateReleases()) {
			ReleaseInfo info = e.info();
			if (info == null || info.number() == null || !titleMatches(info.title(), album.title())) {
				continue;
			}
			String key = info.disc() + "/" + info.number();
			counts.merge(key, 1, Integer::sum);
			byKey.putIfAbsent(key, info);
		}
		return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(v -> {
			ReleaseInfo info = byKey.get(v.getKey());
			// The disc stays the one of the tag: assigning it only to some tracks would split the album
			// into folders. A missing title comes from the same position ("03" becomes "Hole in the Sky").
			String title = withoutTitle && info.trackTitle() != null && !numberOnly(info.trackTitle()) ? info.trackTitle() : m.title();
			Integer number = m.trackNumber() != null ? m.trackNumber() : info.number();
			return t.withMetadata(new TrackMetadata(m.artist(), title, m.album(), m.albumArtist(), m.year(), number, m.discNumber(), m.externalIds()));
		}).orElse(t);
	}

	private static boolean numberOnly(@Nullable String title) {
		return title != null && title.strip().matches("(?i)((track|traccia|pista)\\s*)?\\d{1,3}");
	}

	/**
	 * A one-track album without a year that no release found by Tier 2 confirms
	 * ({@link #withYearFromRelease}): the album tag is often junk ("Stone Free" for "Day Tripper",
	 * "CD-RiP by FBI", 28 "Unknown Year" folders on the run of 2026-10-04) and the track is in fact
	 * isolated, to be placed back on its original album (DESIGN.md, corollary 2). Only if the
	 * fingerprint agrees with the tag artist: a contrast on a track with a tagged album stays on the tag.
	 */
	private static boolean unverifiableAlbum(Album album) {
		return album.tracks().size() == 1 && album.year() == null && album.title() != null;
	}

	/**
	 * Year of a Tier 1 album that has a real album tag but no year (62 albums on the real run, e.g.
	 * "Metallica/Master of Puppets"): Tier 2 looks for a release whose title matches the album tag and
	 * takes its year, the oldest among those found. The tracks are not moved: a "Greatest Hits" owned
	 * as a record stays whole instead of scattering into the original records. It stops at the first
	 * track that finds a match, to limit the external calls.
	 */
	static Album withYearFromRelease(Album album, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup) {
		if (album.year() != null || album.title() == null) {
			return album;
		}
		for (Track t : album.tracks()) {
			if (LocalIdentifier.placeholderOrEmptyAlbumTag(t)) {
				return album;
			}
		}
		for (Track t : album.tracks()) {
			Tier2Candidacy candidacy = Tier2Identifier.identify(t, fingerprintLookup, releaseLookup);
			Optional<Integer> year = candidacy.candidateReleases()
			                                  .stream()
			                                  .filter(e -> e.info() != null && titleMatches(e.info().title(), album
			                                                                                                       .title()))
			                                  .map(e -> e.releaseDate().year())
			                                  .min(Integer::compare);
			if (year.isPresent()) {
				return new Album(album.title(), album.albumArtist(), year.get(), album.completeness(), album.tracks());
			}
		}
		return album;
	}

	// Also without parentheses and disc indications, and with a truncated local title that is the start
	// of the release title: "The Song Remains The Same (Disc Two)" and "The Song Remains the Same",
	// "Warped Tour 2001 Tour Compilat" and "Vans Warped Tour: 2001 Tour Compilation".
	static boolean titleMatches(String releaseTitle, String albumTitle) {
		if (releaseTitle.isBlank()) {
			return false;
		}
		String r = titleCore(releaseTitle);
		String a = titleCore(albumTitle);
		if (sequel(r, a) || sequel(a, r)) {
			return false;
		}
		if (NormalizedLevenshtein.distance(releaseTitle, albumTitle) <= 0.15) {
			return true;
		}
		if (r.isEmpty() || a.isEmpty()) {
			return false;
		}
		// A title contained in the other is enough from 12 characters on: a false "Greatest Hits" in
		// "Greatest Hits Live Broadcast Collection" is then corrected by the year of the release group
		// found by name.
		return r.equals(a) || (Math.min(r.length(), a.length()) >= 12 && (r.contains(a) || a.contains(r)));
	}

	// "Punk Goes Pop 2" after "Punk Goes Pop", "Vol 2" after "Vol": the same title followed by a number
	// is another album. A number elsewhere does not count ("Metropolis Pt 2 Scenes From a Memory").
	private static boolean sequel(String longer, String shorter) {
		return !shorter.isEmpty() && longer.startsWith(shorter) && longer.substring(shorter.length())
		                                                                 .strip()
		                                                                 .matches("\\d+.*");
	}

	private static String titleCore(String title) {
		return title.toLowerCase(java.util.Locale.ROOT)
		            .replaceAll("[(\\[][^)\\]]*[)\\]]", " ")
		            .replaceAll("\\b(cd|disc|disco|disk)\\s*([0-9]+|one|two|three|four)\\b", " ")
		            .replace("&", " and ")
		            .replaceAll("[^\\p{L}\\p{N}]+", " ")
		            .replaceAll("\\b\\d+(st|nd|rd|th)?\\s+anniversary\\b", " ")
		            .replaceAll("\\b(the|\\d+(st|nd|rd|th)|anniversary|remastered|remaster|edition|deluxe|expanded|special|limited|bonus|original|motion|picture|music|from|ost|soundtrack)\\b", " ")
		            .replaceAll("\\s+", " ")
		            .strip();
	}

	private static Optional<String> contrastIfApplied(Tier2Candidacy candidacy) {
		return candidacy.selectedRelease().isPresent() ? candidacy.artistContrastingTag() : Optional.empty();
	}

	private static Optional<TextClassification> tier3Suggestion(Track track, Optional<Release> release, TextClassificationPort textClassification) {
		if (release.isPresent()) {
			return Optional.empty();
		}
		if (textClassification == null) {
			return Optional.empty();
		}
		return textClassification.classify(track);
	}
}
