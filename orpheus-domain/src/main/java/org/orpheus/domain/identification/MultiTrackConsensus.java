package org.orpheus.domain.identification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

/**
 * Multi-track consensus (SPEC.md 4.5, ADR-001): on a group of tracks with placeholder tags already
 * produced by {@link LocalIdentifier} (Tier 0/1), forces the whole group onto the same release when at
 * least {@value #THRESHOLD_PERCENT}% of the tracks converge individually (through
 * {@link Tier2Identifier}) on the same release, with the safeguard that the forced minority tracks must
 * still have a match, not necessarily the best one, on the winning release. Below the threshold every
 * track keeps its own individual Tier 2 identification, with no intermediate levels (SPEC.md 4.5,
 * explicitly postponed).
 *
 * <p>It works only <b>inside</b> a single {@link Album} already produced by Tier 0/1: it does not merge
 * different groups or folders. It is a safety precondition for a future retroactive cross-folder
 * reconstruction (not implemented here), not the mechanism that would do it.
 */
public final class MultiTrackConsensus {

	/**
	 * Convergence threshold of ADR-001/SPEC 4.5. Kept as an integer (percent, not {@code double}) for
	 * the arithmetic of {@link #compute}: it avoids any floating point rounding ambiguity in a
	 * comparison that decides whether to force a whole group.
	 */
	static final long THRESHOLD_PERCENT = 65;

	private MultiTrackConsensus() {
	}

	/**
	 * A group is eligible for consensus only if Tier 0/1 consulted no external source
	 * ({@link AlbumCompleteness#UNVERIFIED}) and all the tracks have an empty or blank artist: the
	 * "tags never really written" signal of SPEC 4.5, distinct from the single-track Tier 2 gate
	 * ({@link LocalIdentifier#placeholderOrEmptyAlbumTag}, which looks at the album tag). A folder with
	 * real artist tags but an album still to organize (e.g. CD1/CD2 already split by hand by the user)
	 * is not eligible: that split is a choice already made upstream, not an ambiguity to solve here.
	 */
	public static boolean isEligibleGroup(Album album) {
		Objects.requireNonNull(album, "album");
		if (album.completeness() != AlbumCompleteness.UNVERIFIED) {
			return false;
		}
		return album.tracks().stream().allMatch(MultiTrackConsensus::emptyArtist);
	}

	private static boolean emptyArtist(Track t) {
		String artist = t.metadata().artist();
		return artist == null || artist.isBlank();
	}

	public static ConsensusResult compute(Album album, FingerprintLookupPort fingerprintLookup, ReleaseLookupPort releaseLookup) {
		Objects.requireNonNull(album, "album");
		Objects.requireNonNull(fingerprintLookup, "fingerprintLookup");
		Objects.requireNonNull(releaseLookup, "releaseLookup");

		List<Tier2Candidacy> candidacies = new ArrayList<>();
		for (Track t : album.tracks()) {
			candidacies.add(Tier2Identifier.identify(t, fingerprintLookup, releaseLookup));
		}

		// The denominator is always the total number of tracks of the group: a track without any
		// fingerprint candidate counts against the proportion but can never add to the count of a
		// release, so it can never be "majority".
		Map<String, Long> countsByRelease = new LinkedHashMap<>();
		for (Tier2Candidacy c : candidacies) {
			c.selectedRelease().ifPresent(e -> countsByRelease.merge(e.releaseId(), 1L, Long::sum));
		}

		long total = album.tracks().size();
		long max = 0;
		for (long count : countsByRelease.values()) {
			if (count > max) {
				max = count;
			}
		}

		if (max == 0 || max * 100L < THRESHOLD_PERCENT * total) {
			List<ConsensusTrackOutcome> individualOutcomes = candidacies.stream()
			                                                            .map(c -> new ConsensusTrackOutcome(c.track(), c.selectedRelease(), false, c.selectedRelease()
			                                                                                                                                        .flatMap(c::recordingOf)))
			                                                            .toList();
			return new ConsensusResult(Optional.empty(), individualOutcomes);
		}

		List<String> topReleaseIds = new ArrayList<>();
		for (var entry : countsByRelease.entrySet()) {
			if (entry.getValue() == max) {
				topReleaseIds.add(entry.getKey());
			}
		}
		Release winner = breakTopTie(candidacies, topReleaseIds);

		List<ConsensusTrackOutcome> outcomes = new ArrayList<>();
		for (Tier2Candidacy c : candidacies) {
			boolean alreadySelectedWinner = c.selectedRelease()
			                                 .map(Release::releaseId)
			                                 .map(winner.releaseId()::equals)
			                                 .orElse(false);
			boolean hasMatchAmongCandidates = c.candidateReleases()
			                                   .stream()
			                                   .anyMatch(e -> e.releaseId().equals(winner.releaseId()));
			if (alreadySelectedWinner || hasMatchAmongCandidates) {
				// The very same release for all the forced tracks (SPEC 4.5: "on the very same release"),
				// but each one with its own Release of that release: disc, number and title of the track
				// belong to the recording-release pair (corrective plan, phase 2).
				Release own = c.candidateReleases()
				               .stream()
				               .filter(e -> e.releaseId().equals(winner.releaseId()))
				               .findFirst()
				               .orElse(winner);
				outcomes.add(new ConsensusTrackOutcome(c.track(), Optional.of(own), true, c.recordingOf(own)));
			} else {
				outcomes.add(new ConsensusTrackOutcome(c.track(), c.selectedRelease(), false, c.selectedRelease()
				                                                                               .flatMap(c::recordingOf)));
			}
		}
		return new ConsensusResult(Optional.of(winner), outcomes);
	}

	/**
	 * Breaks the tie by oldest date ({@link ReleaseSelector}) when several releases reach the same
	 * maximum number of converging tracks: the implementation of "when there are several same-named
	 * releases, the rule of point 2 of Tier 2 applies again" (SPEC 4.5).
	 *
	 * <p><b>Unreachable by construction from {@link #compute} with {@link #THRESHOLD_PERCENT} = 65:</b>
	 * two distinct release ids are disjoint sets of tracks (every track selects a single release), so if
	 * both reached the same maximum {@code m} it would take {@code 2m &lt;= total}, that is
	 * {@code m &lt;= 50%} of the group: below the threshold, so {@code compute} would have returned
	 * before getting here. Implementing it is still right (faithful to the SPEC text, cheap, defensive
	 * for a possible different future threshold); it is tested directly, not through {@link #compute},
	 * because the latter can never reach it at a 65% threshold.
	 */
	static Release breakTopTie(List<Tier2Candidacy> candidacies, List<String> tiedReleaseIds) {
		Map<String, Release> uniqueReleases = new LinkedHashMap<>();
		for (Tier2Candidacy c : candidacies) {
			c.selectedRelease().ifPresent(e -> {
				if (tiedReleaseIds.contains(e.releaseId())) {
					uniqueReleases.putIfAbsent(e.releaseId(), e);
				}
			});
		}
		return ReleaseSelector.choose(new ArrayList<>(uniqueReleases.values())).orElseThrow();
	}
}
