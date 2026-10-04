package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.AlbumCompleteness;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.TextClassificationPort;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

class IdentificationPipelineTest {

	private static final FingerprintLookupPort NO_FINGERPRINTS = t -> FingerprintLookupResult.empty();
	private static final ReleaseLookupPort NO_RELEASES = r -> List.of();
	private static final TextClassificationPort NO_TEXT = t -> Optional.empty();

	@Test
	void anIsolatedTrackWithoutMatchStaysEmpty() {
		Track t = track("t.mp3", null, null);
		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), NO_FINGERPRINTS, NO_RELEASES, NO_TEXT);
		assertThat(outcomes).hasSize(1);
		assertThat(outcomes.get(0).album()).isNull();
		assertThat(outcomes.get(0).release()).isEmpty();
		assertThat(outcomes.get(0).tier3Suggestion()).isEmpty();
	}

	@Test
	void anIsolatedTrackWithATier3Suggestion() {
		Track t = track("Artist - Title.mp3", null, null);
		TextClassificationPort heuristic = tr -> Optional.of(new TextClassification(Optional.of("A"), Optional.empty(), Optional.of("T"), 0.8));
		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), NO_FINGERPRINTS, NO_RELEASES, heuristic);
		assertThat(outcomes.get(0).tier3Suggestion()).isPresent();
		assertThat(outcomes.get(0).tier3Suggestion().get().confidence()).isEqualTo(0.8);
	}

	@Test
	void anAlbumWithARealTagGoesThroughNeitherTier2NorTier3() {
		Track t = track("t.mp3", "Artist", "Album");
		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), NO_FINGERPRINTS, NO_RELEASES, NO_TEXT);
		assertThat(outcomes.get(0).album()).isNotNull();
		assertThat(outcomes.get(0).release()).isEmpty();
		assertThat(outcomes.get(0).tier3Suggestion()).isEmpty();
	}

	@Test
	void aPlaceholderAlbumWithoutTier2ButWithTier3() {
		Track t1 = track("01.mp3", null, null);
		Track t2 = track("02.mp3", null, null);
		TextClassificationPort heuristic = tr -> Optional.of(new TextClassification(Optional.of("A"), Optional.empty(), Optional.of("T"), 0.7));
		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t1, t2), NO_FINGERPRINTS, NO_RELEASES, heuristic);
		for (TrackOutcome e : outcomes) {
			assertThat(e.album()).isNotNull();
			assertThat(e.release()).isEmpty();
			assertThat(e.tier3Suggestion()).isPresent();
		}
	}

	@Test
	void multiTrackConsensusWithAWinningTier2() {
		Track t1 = track("01.mp3", null, null);
		Track t2 = track("02.mp3", null, null);
		Track t3 = track("03.mp3", null, null);

		Release release = new Release("rel", ReleaseType.ALBUM, ReleaseDate.yearOnly(2000));
		FingerprintLookupPort fingerprints = tr -> FingerprintLookupResult.idsOnly(List.of("rec"));
		ReleaseLookupPort releases = r -> List.of(release);

		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t1, t2, t3), fingerprints, releases, NO_TEXT);
		for (TrackOutcome e : outcomes) {
			assertThat(e.release()).hasValue(release);
			assertThat(e.forced()).isTrue();
			assertThat(e.tier3Suggestion()).isEmpty();
		}
	}

	@Test
	void individualTier2OnANonEligibleAlbum() {
		Track t1 = track("01.mp3", "Artist", null);
		Track t2 = track("02.mp3", "Artist", null);

		Release release = new Release("rel", ReleaseType.ALBUM, ReleaseDate.yearOnly(2000));
		// Artist known in the tag: the candidate must match it to survive the filter (ADR-0018).
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(
		        Optional.empty(), List.of(new CandidateRecording("rec", "Title", List.of("Artist"), Optional.empty())));
		ReleaseLookupPort releases = r -> List.of(release);

		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t1, t2), fingerprints, releases, NO_TEXT);
		for (TrackOutcome e : outcomes) {
			assertThat(e.release()).hasValue(release);
			assertThat(e.forced()).isFalse();
		}
	}

	private static Track track(String fileName, String artist, String album) {
		Path path = Path.of("/tmp/test").resolve(fileName);
		return new Track(
		        path, new TrackMetadata(artist, null, album, null, null, null, null, ExternalIds.unknown()), new Provenance(path, ProvenanceState.INTACT));
	}

	private static Album taggedAlbum(String title, Track... tracks) {
		return new Album(title, "Metallica", null, AlbumCompleteness.UNVERIFIED, List.of(tracks));
	}

	@Test
	void aTaggedAlbumWithoutAYearTakesTheYearOfTheReleaseWithTheSameTitle() {
		Track t = track("Master.mp3", "Metallica", "Master of Puppets");
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Master of Puppets", List.of("Metallica"), Optional.empty())));
		ReleaseLookupPort releases = r -> List.of(
		        new Release("compilation", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980), List.of("Metallica"), false, new ReleaseInfo("S&M", "Metallica", "rec", 1, 3, "Master of Puppets")), new Release("album", ReleaseType.ALBUM, ReleaseDate.yearOnly(1986), List.of("Metallica"), false, new ReleaseInfo("Master of Puppets", "Metallica", "rec", 1, 2, "Master of Puppets")));

		Album album = IdentificationPipeline.withYearFromRelease(taggedAlbum("Master of Puppets", t), fingerprints, releases);

		assertThat(album.year()).isEqualTo(1986);
		assertThat(album.tracks()).containsExactly(t);
	}

	@Test
	void withoutAReleaseWithTheSameTitleTheYearStaysAbsent() {
		Track t = track("GH.mp3", "Guns N Roses", "Greatest Hits");
		FingerprintLookupPort fingerprints = tr -> FingerprintLookupResult.idsOnly(List.of("rec"));
		ReleaseLookupPort releases = r -> List.of(new Release("album", ReleaseType.ALBUM, ReleaseDate.yearOnly(1987), List.of(), false, new ReleaseInfo("Appetite for Destruction", "Guns N' Roses", "rec", 1, 1, "Welcome to the Jungle")));

		assertThat(IdentificationPipeline.withYearFromRelease(taggedAlbum("Greatest Hits", t), fingerprints, releases)
		                                 .year()).isNull();
	}

	// "Beatles/Stone Free/ - Day Tripper.mp3": a one-track album, no "Stone Free" release.
	@Test
	void anUnverifiableOneTrackAlbumIsPlacedByTier2() {
		Track t = track("Day Tripper.mp3", "The Beatles", "Stone Free");
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Day Tripper", List.of("The Beatles"), Optional.empty())));
		Release single = new Release("dt", ReleaseType.SINGLE, ReleaseDate.yearOnly(1965), List.of("The Beatles"), false, new ReleaseInfo("Day Tripper / We Can Work It Out", "The Beatles", "rec", 1, 1, "Day Tripper"));

		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), fingerprints, r -> List.of(single), NO_TEXT);

		assertThat(outcomes.get(0).release()).contains(single);
	}

	// With the fingerprint contrasting the tag artist, the tagged album stays as it is.
	@Test
	void aOneTrackAlbumContrastingTheTagStays() {
		Track t = track("Imagine.mp3", "Beatles", "Imagine");
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Imagine", List.of("John Lennon"), Optional.empty())));
		Release album = new Release("im", ReleaseType.ALBUM, ReleaseDate.yearOnly(1971), List.of("John Lennon"), false, new ReleaseInfo("Imagine", "John Lennon", "rec", 1, 1, "Imagine"));

		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), fingerprints, r -> List.of(album), NO_TEXT);

		assertThat(outcomes.get(0).album().year()).isEqualTo(1971);
		assertThat(outcomes.get(0).release()).isEmpty();
	}

	// "Imagine" credited to the Beatles only on a bootleg: the album of the tag stays.
	@Test
	void aOneTrackAlbumWithOnlyASecondaryReleaseStays() {
		Track t = track("Imagine.mp3", "The Beatles", "Imagine");
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Imagine", List.of("The Beatles"), Optional.empty())));
		Release bootleg = new Release("art", ReleaseType.ALBUM, ReleaseDate.yearOnly(1995), List.of("The Beatles"), true, new ReleaseInfo("Artifacts III", "The Beatles", "rec", 1, 16, "Imagine"));

		List<TrackOutcome> outcomes = IdentificationPipeline.run(List.of(t), fingerprints, r -> List.of(bootleg), NO_TEXT);

		assertThat(outcomes.get(0).release()).isEmpty();
	}

	@Test
	void albumTitleWithDiscOrTruncated() {
		assertThat(IdentificationPipeline.titleMatches("The Song Remains the Same", "The Song Remains The Same (Disc Two)")).isTrue();
		assertThat(IdentificationPipeline.titleMatches("Vans Warped Tour: 2001 Tour Compilation", "Warped Tour 2001 Tour Compilat")).isTrue();
		assertThat(IdentificationPipeline.titleMatches("Remasters", "Remasters - CD 2")).isTrue();
		assertThat(IdentificationPipeline.titleMatches("Greatest Hits", "Appetite for Destruction")).isFalse();
		assertThat(IdentificationPipeline.titleMatches("Burn", "Burn / Stormbringer")).isFalse();
		assertThat(IdentificationPipeline.titleMatches("Made in Japan", "Made in Japan- 25 Anniversary Remastered Edition")).isTrue();
		assertThat(IdentificationPipeline.titleMatches("Orange County: The Soundtrack", "Orange County Soundtrack")).isTrue();
		assertThat(IdentificationPipeline.titleMatches("Punk Goes Pop 2", "Punk Goes Pop")).isFalse();
		assertThat(IdentificationPipeline.titleMatches("Metropolis Pt. 2: Scenes From a Memory", "Scenes From a Memory")).isTrue();
	}

	// "Yellowcard/Ocean Avenue/ - Breathing.mp3": album tag without a track number.
	@Test
	void theMissingNumberComesFromThePositionInTheAlbumReleases() {
		Track t = track("Breathing.mp3", "Yellowcard", "Ocean Avenue");
		Album album = new Album("Ocean Avenue", "Yellowcard", 2003, AlbumCompleteness.UNVERIFIED, List.of(t));
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Breathing", List.of("Yellowcard"), Optional.empty())));
		ReleaseLookupPort releases = r -> List.of(
		        new Release("oa", ReleaseType.ALBUM, ReleaseDate.yearOnly(2003), List.of("Yellowcard"), false, new ReleaseInfo("Ocean Avenue", "Yellowcard", "rec", 1, 2, "Breathing")), new Release("oa-jp", ReleaseType.ALBUM, ReleaseDate.yearOnly(2003), List.of("Yellowcard"), false, new ReleaseInfo("Ocean Avenue", "Yellowcard", "rec", 1, 2, "Breathing")), new Release("live", ReleaseType.ALBUM, ReleaseDate.yearOnly(2006), List.of("Yellowcard"), true, new ReleaseInfo("Live", "Yellowcard", "rec", 1, 9, "Breathing")));

		Track numbered = IdentificationPipeline.withNumberFromRelease(t, album, fingerprints, releases);

		assertThat(numbered.metadata().trackNumber()).isEqualTo(2);
		assertThat(numbered.metadata().discNumber()).isNull();
	}

	@Test
	void aNumericTitleIsReplacedOnlyByARealTitle() {
		Path p = Path.of("/tmp/test/03.mp3");
		Track t = new Track(p, new TrackMetadata("Black Sabbath", "03", "Sabotage", null, null, null, null, ExternalIds.unknown()), new Provenance(p, ProvenanceState.INTACT));
		Album album = new Album("Sabotage", "Black Sabbath", 1975, AlbumCompleteness.UNVERIFIED, List.of(t));
		FingerprintLookupPort fingerprints = tr -> new FingerprintLookupResult(Optional.empty(), List.of(new CandidateRecording("rec", "Hole in the Sky", List.of("Black Sabbath"), Optional.empty())));
		ReleaseLookupPort releases = r -> List.of(new Release("s", ReleaseType.ALBUM, ReleaseDate.yearOnly(1975), List.of("Black Sabbath"), false, new ReleaseInfo("Sabotage", "Black Sabbath", "rec", 1, 1, "Hole in the Sky")));

		Track corrected = IdentificationPipeline.withNumberFromRelease(t, album, fingerprints, releases);

		assertThat(corrected.metadata().title()).isEqualTo("Hole in the Sky");
		assertThat(corrected.metadata().trackNumber()).isEqualTo(1);
	}
}
