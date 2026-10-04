package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

class MultiTrackConsensusTest {

	private static Track trackWithEmptyArtist(String fileName) {
		return trackWithArtist(fileName, "");
	}

	private static Track trackWithArtist(String fileName, String artist) {
		var metadata = new TrackMetadata(artist, null, null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/music/group", fileName), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/" + fileName), metadata, provenance);
	}

	private static Album album(AlbumCompleteness completeness, List<Track> tracks) {
		return new Album("Group", null, null, completeness, tracks);
	}

	private static Album album(List<Track> tracks) {
		return album(AlbumCompleteness.UNVERIFIED, tracks);
	}

	private static FingerprintLookupPort fingerprints(Map<Track, List<String>> byTrack) {
		return t -> FingerprintLookupResult.idsOnly(byTrack.getOrDefault(t, List.of()));
	}

	private static ReleaseLookupPort releases(Map<String, List<Release>> byRecordingId) {
		return recordingId -> byRecordingId.getOrDefault(recordingId, List.of());
	}

	@Test
	void eligibleOnlyWithUnverifiedCompletenessAndAllArtistsEmpty() {
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");

		assertThat(MultiTrackConsensus.isEligibleGroup(album(List.of(t1, t2)))).isTrue();
		assertThat(MultiTrackConsensus.isEligibleGroup(album(AlbumCompleteness.PARTIAL, List.of(t1, t2)))).isFalse();

		// The CD1/CD2 case of SPEC 4.5: real artist tags, only the album is not organized yet. It is not
		// an ambiguity for the consensus to act on, it is a choice the user already made.
		Track withRealArtist = trackWithArtist("03.mp3", "Real Artist");
		assertThat(MultiTrackConsensus.isEligibleGroup(album(List.of(t1, withRealArtist)))).isFalse();
	}

	@Test
	void aboveTheThresholdTheMajorityIsForced() {
		Release winning = new Release("winning", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Track t3 = trackWithEmptyArtist("03.mp3");
		var fingerprints = fingerprints(Map.of(t1, List.of("rec"), t2, List.of("rec"), t3, List.of("rec")));
		var releases = releases(Map.of("rec", List.of(winning)));

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2, t3)), fingerprints, releases);

		assertThat(result.winningRelease()).contains(winning);
		assertThat(result.tracks()).allSatisfy(e -> {
			assertThat(e.forced()).isTrue();
			assertThat(e.assignedRelease()).contains(winning);
		});
	}

	@Test
	void aMinorityTrackWithAMatchOnTheWinningReleaseIsForced() {
		Release winning = new Release("winning", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		// The individual match of the minority track is stronger (older date), but the winning release
		// is still among its candidates: the SPEC 4.5 safeguard ("a match, not necessarily the best one")
		// must still force it.
		Release minorityIndividualChoice = new Release("minority", ReleaseType.ALBUM, ReleaseDate.yearOnly(1970));

		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Track t3 = trackWithEmptyArtist("03.mp3");
		var fingerprints = fingerprints(
		        Map.of(t1, List.of("rec-winning"), t2, List.of("rec-winning"), t3, List.of("rec-minority", "rec-winning-weak")));
		var releases = releases(
		        Map.of(
		                "rec-winning", List.of(winning), "rec-minority", List.of(minorityIndividualChoice), "rec-winning-weak", List.of(winning)));

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2, t3)), fingerprints, releases);

		assertThat(result.winningRelease()).contains(winning);
		ConsensusTrackOutcome minorityResult = result.tracks()
		                                             .stream()
		                                             .filter(e -> e.track().equals(t3))
		                                             .findFirst()
		                                             .orElseThrow();
		assertThat(minorityResult.forced()).isTrue();
		assertThat(minorityResult.assignedRelease()).contains(winning);
	}

	@Test
	void aMinorityTrackWithoutAnyMatchStaysIndividual() {
		Release winning = new Release("winning", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		Release minorityOnly = new Release("only-minority", ReleaseType.ALBUM, ReleaseDate.yearOnly(1970));

		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Track t3 = trackWithEmptyArtist("03.mp3");
		var fingerprints = fingerprints(Map.of(t1, List.of("rec-winning"), t2, List.of("rec-winning"), t3, List.of("rec-only")));
		var releases = releases(Map.of("rec-winning", List.of(winning), "rec-only", List.of(minorityOnly)));

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2, t3)), fingerprints, releases);

		assertThat(result.winningRelease()).contains(winning);
		ConsensusTrackOutcome minorityResult = result.tracks()
		                                             .stream()
		                                             .filter(e -> e.track().equals(t3))
		                                             .findFirst()
		                                             .orElseThrow();
		assertThat(minorityResult.forced()).isFalse();
		assertThat(minorityResult.assignedRelease()).contains(minorityOnly);
	}

	@Test
	void belowTheThresholdEveryTrackGoesBackToIndividualBehavior() {
		Release a = new Release("a", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		Release b = new Release("b", ReleaseType.ALBUM, ReleaseDate.yearOnly(1970));
		Release c = new Release("c", ReleaseType.ALBUM, ReleaseDate.yearOnly(1960));

		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Track t3 = trackWithEmptyArtist("03.mp3");
		// Three different releases, none reaches 65% (1/3 each).
		var fingerprints = fingerprints(Map.of(t1, List.of("rec-a"), t2, List.of("rec-b"), t3, List.of("rec-c")));
		var releases = releases(Map.of("rec-a", List.of(a), "rec-b", List.of(b), "rec-c", List.of(c)));

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2, t3)), fingerprints, releases);

		assertThat(result.winningRelease()).isEmpty();
		assertThat(result.tracks()).allSatisfy(e -> assertThat(e.forced()).isFalse());
		assertThat(result.tracks().stream().map(e -> e.assignedRelease().orElseThrow()).toList())
		                                                                                         .containsExactlyInAnyOrder(a, b, c);
	}

	@Test
	void allTracksWithoutIdentificationStayIndividual() {
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		var fingerprints = fingerprints(Map.of());
		var releases = releases(Map.of());

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2)), fingerprints, releases);

		assertThat(result.winningRelease()).isEmpty();
		assertThat(result.tracks()).allSatisfy(e -> {
			assertThat(e.forced()).isFalse();
			assertThat(e.assignedRelease()).isEmpty();
		});
	}

	@Test
	void twoTracksNeedAHundredPercentConsensusNotFifty() {
		Release a = new Release("a", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		var fingerprints = fingerprints(Map.of(t1, List.of("rec-a")));
		var releases = releases(Map.of("rec-a", List.of(a)));

		// 1/2 = 50%, below 65%: no forcing even though it is the only release found.
		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2)), fingerprints, releases);
		assertThat(result.winningRelease()).isEmpty();

		// 2/2 = 100%: forces the group.
		var fingerprintsForBoth = fingerprints(Map.of(t1, List.of("rec-a"), t2, List.of("rec-a")));
		ConsensusResult fullResult = MultiTrackConsensus.compute(album(List.of(t1, t2)), fingerprintsForBoth, releases);
		assertThat(fullResult.winningRelease()).contains(a);
	}

	@Test
	void exactlySixtyFivePercentOfTwentyTracksForcesTheGroupBelowItDoesNot() {
		Release a = new Release("a", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));
		ReleaseLookupPort releases = releases(Map.of("rec-a", List.of(a)));

		ConsensusResult result13of20 = MultiTrackConsensus.compute(albumOfTwenty(), fingerprintsOfTwenty(13), releases);
		assertThat(result13of20.winningRelease()).as("13/20 = exactly 65%%, must force").contains(a);

		ConsensusResult result12of20 = MultiTrackConsensus.compute(albumOfTwenty(), fingerprintsOfTwenty(12), releases);
		assertThat(result12of20.winningRelease()).as("12/20 = 60%%, below the threshold").isEmpty();
	}

	private static Album albumOfTwenty() {
		List<Track> tracks = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			tracks.add(trackWithEmptyArtist(i + ".mp3"));
		}
		return album(tracks);
	}

	private static FingerprintLookupPort fingerprintsOfTwenty(int converging) {
		Map<Track, List<String>> byTrack = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 20; i++) {
			Track t = trackWithEmptyArtist(i + ".mp3");
			byTrack.put(t, i < converging ? List.of("rec-a") : List.of());
		}
		return fingerprints(byTrack);
	}

	@Test
	void breakTopTieChoosesTheOldestAmongTheTied() {
		// Called directly: compute() can never reach a tie at the top with THRESHOLD_PERCENT=65 (see the
		// Javadoc of MultiTrackConsensus.breakTopTie): two distinct tied release ids would imply max <=
		// 50% of the group, so below the threshold.
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Release recent = new Release("recent", ReleaseType.ALBUM, ReleaseDate.yearOnly(1990));
		Release old = new Release("old", ReleaseType.ALBUM, ReleaseDate.yearOnly(1970));
		Tier2Candidacy c1 = new Tier2Candidacy(t1, List.of(recent), Optional.of(recent));
		Tier2Candidacy c2 = new Tier2Candidacy(t2, List.of(old), Optional.of(old));

		Release winner = MultiTrackConsensus.breakTopTie(List.of(c1, c2), List.of("recent", "old"));

		assertThat(winner).isEqualTo(old);
	}

	// Every forced track receives its own Release of the winning release (position and title belong to
	// the recording-release pair), not that of another track.
	@Test
	void everyForcedTrackReceivesItsOwnPositionInTheWinningRelease() {
		Track t1 = trackWithEmptyArtist("01.mp3");
		Track t2 = trackWithEmptyArtist("02.mp3");
		Track t3 = trackWithEmptyArtist("03.mp3");
		ReleaseLookupPort releases = recordingId -> List.of(new Release("rel", ReleaseType.ALBUM, ReleaseDate.yearOnly(1999), List.of(), false, new ReleaseInfo("Title", "", recordingId, 1, Integer.valueOf(recordingId.substring(4)), "T" + recordingId)));
		var fingerprints = fingerprints(Map.of(t1, List.of("rec-1"), t2, List.of("rec-2"), t3, List.of("rec-3")));

		ConsensusResult result = MultiTrackConsensus.compute(album(List.of(t1, t2, t3)), fingerprints, releases);

		assertThat(result.tracks()).allMatch(ConsensusTrackOutcome::forced);
		assertThat(result.tracks()).extracting(e -> e.assignedRelease().orElseThrow().info().number())
		                           .containsExactly(1, 2, 3);
	}
}
