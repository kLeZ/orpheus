package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;

class Tier2IdentifierTest {

	private static Track track(String fileName) {
		var metadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/musica", fileName), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/" + fileName), metadata, provenance);
	}

	private static FingerprintLookupPort fingerprints(List<String> recordingIds) {
		return t -> FingerprintLookupResult.idsOnly(recordingIds);
	}

	private static ReleaseLookupPort releases(Map<String, List<Release>> byRecordingId) {
		return recordingIds -> byRecordingId.getOrDefault(recordingIds, List.of());
	}

	@Test
	void noCandidateRecordingIdProducesAnEmptyCandidacy() {
		Track t = track("01.mp3");

		Tier2Candidacy candidacy = Tier2Identifier.identify(t, fingerprints(List.of()), releases(Map.of()));

		assertThat(candidacy.candidateReleases()).isEmpty();
		assertThat(candidacy.selectedRelease()).isEmpty();
	}

	@Test
	void aRecordingIdWithASingleReleaseIsSelected() {
		Track t = track("01.mp3");
		Release release = new Release("release-1", ReleaseType.ALBUM, ReleaseDate.yearOnly(1980));

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprints(List.of("rec-1")), releases(Map.of("rec-1", List.of(release))));

		assertThat(candidacy.candidateReleases()).containsExactly(release);
		assertThat(candidacy.selectedRelease()).contains(release);
	}

	@Test
	void severalRecordingIdsAggregateTheReleasesAndTheSelectorChoosesOnTheUnion() {
		Track t = track("01.mp3");
		Release single = new Release("single", ReleaseType.SINGLE, ReleaseDate.exact(1974, 1, 1));
		Release album = new Release("album", ReleaseType.ALBUM, ReleaseDate.yearOnly(1975));

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprints(List.of("rec-single", "rec-album")), releases(Map.of("rec-single", List.of(single), "rec-album", List.of(album))));

		assertThat(candidacy.candidateReleases()).containsExactlyInAnyOrder(single, album);
		// Rule 1 of ReleaseSelector applied to the union: Album beats Single even if the latter
		// comes from a different recording id.
		assertThat(candidacy.selectedRelease()).contains(album);
	}

	// ADR-0023: the recording id of the placement leads back to the score and duration of the recording.
	@Test
	void theRecordingOfTheChosenReleaseCarriesScoreAndDuration() {
		Track t = track("01.mp3");
		CandidateRecording studio = new CandidateRecording("rec-studio", null, List.of(), Optional.of(237), Optional.of(0.64));
		CandidateRecording live = new CandidateRecording("rec-live", null, List.of(), Optional.of(238), Optional.of(0.5));
		Release album = new Release("album", ReleaseType.ALBUM, ReleaseDate.yearOnly(2003), List.of(), false, new ReleaseInfo("Fallen", "Evanescence", "rec-studio", 1, 2, null));
		Release liveRelease = new Release("live", ReleaseType.ALBUM, ReleaseDate.yearOnly(2004), List.of(), true, new ReleaseInfo("Anywhere but Home", "Evanescence", "rec-live", 1, 1, null));

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, x -> new FingerprintLookupResult(Optional.of(237), List.of(studio, live)), releases(Map.of("rec-studio", List.of(album), "rec-live", List.of(liveRelease))));

		assertThat(candidacy.selectedRelease()).contains(album);
		assertThat(candidacy.recordingOf(album)).contains(studio);
		assertThat(candidacy.recordingOf(liveRelease)).contains(live);
	}

	@Test
	void withoutReleasesForTheArtistRecordingTheFallbackIsUsed() {
		var metadata = new TrackMetadata("Ronnie James Dio", "Holy Diver", null, null, null, null, null, ExternalIds.UNKNOWN);
		Track t = new Track(Path.of("/dest/hd.mp3"), metadata, new Provenance(Path.of("/music/hd.mp3"), ProvenanceState.INTACT));
		FingerprintLookupResult cluster = new FingerprintLookupResult(Optional.of(349), List.of(new CandidateRecording("rjd", "Holy Diver", List.of("Ronnie James Dio"), Optional.of(349)), new CandidateRecording("dio", "Holy Diver", List.of("Dio"), Optional.of(352))));
		Release album = new Release("holy-diver", ReleaseType.ALBUM, ReleaseDate.yearOnly(1983), List.of("Dio"), false, new ReleaseInfo("Holy Diver", "Dio", "dio", 1, 5, "Holy Diver"));

		Tier2Candidacy candidacy = Tier2Identifier.identify(t, x -> cluster, releases(Map.of("dio", List.of(album))));

		assertThat(candidacy.selectedRelease()).contains(album);
		assertThat(candidacy.artistContrastingTag()).hasValue("Dio");
	}

	// A cover often exists only on a compilation or a live record: it stays with the tag artist (Foo
	// Fighters "Tiny Dancer" live, Yellowcard "Everywhere" from "Punk Goes Pop", real run of 2026-10-04).
	@Test
	void aCoverOnACompilationDoesNotYieldToTheOriginal() {
		var metadata = new TrackMetadata("Foo Fighters", "Tiny Dancer", null, null, null, null, null, ExternalIds.UNKNOWN);
		Track t = new Track(Path.of("/dest/td.mp3"), metadata, new Provenance(Path.of("/music/td.mp3"), ProvenanceState.INTACT));
		FingerprintLookupResult cluster = new FingerprintLookupResult(Optional.of(290), List.of(new CandidateRecording("ff", "Tiny Dancer", List.of("Foo Fighters"), Optional.of(290)), new CandidateRecording("ej", "Tiny Dancer", List.of("Elton John"), Optional.of(292)), new CandidateRecording("ej2", "Tiny Dancer", List.of("Elton John"), Optional.of(291))));
		Release live = new Release("live", ReleaseType.SINGLE, ReleaseDate.yearOnly(2001), List.of("Foo Fighters"), true, new ReleaseInfo("Tiny Dancer (Live)", "Foo Fighters", "ff", 1, 1, "Tiny Dancer"));
		Release original = new Release("madman", ReleaseType.ALBUM, ReleaseDate.yearOnly(1971), List.of("Elton John"), false, new ReleaseInfo("Madman Across the Water", "Elton John", "ej", 1, 1, "Tiny Dancer"));

		Tier2Candidacy candidacy = Tier2Identifier.identify(t, x -> cluster, releases(Map.of("ff", List.of(live), "ej", List.of(original))));

		assertThat(candidacy.selectedRelease()).contains(live);
	}

	@Test
	void aLiveTrackInAReleaseDoesNotWinOverTheTitleWithoutVersion() {
		var metadata = new TrackMetadata("Bon Jovi", "Always", null, null, null, null, null, ExternalIds.UNKNOWN);
		Track t = new Track(Path.of("/dest/al.mp3"), metadata, new Provenance(Path.of("/music/al.mp3"), ProvenanceState.INTACT));
		FingerprintLookupResult cluster = new FingerprintLookupResult(Optional.of(354), List.of(new CandidateRecording("rec", "Always", List.of("Bon Jovi"), Optional.of(353))));
		Release theseDays = new Release("td", ReleaseType.ALBUM, ReleaseDate.yearOnly(1995), List.of("Bon Jovi"), false, new ReleaseInfo("These Days", "Bon Jovi", "rec", 2, 1, "Always (live in Montreal)"));
		Release single = new Release("single", ReleaseType.SINGLE, ReleaseDate.yearOnly(1994), List.of("Bon Jovi"), false, new ReleaseInfo("Always", "Bon Jovi", "rec", 1, 1, "Always"));

		Tier2Candidacy candidacy = Tier2Identifier.identify(t, x -> cluster, releases(Map.of("rec", List.of(theseDays, single))));

		assertThat(candidacy.selectedRelease()).contains(single);
	}

	private static FingerprintLookupPort fingerprintsWithArtist(String artist, String... recordingIds) {
		return t -> new FingerprintLookupResult(
		        Optional.empty(), List.of(recordingIds)
		                              .stream()
		                              .map(id -> new CandidateRecording(id, "Title", List.of(artist), Optional.empty()))
		                              .toList());
	}

	// Nickelback "How You Remind Me" had ended up on "Monitor This" (Various Artists, 2000), older than
	// the original album: with ADR-0018 point B the compilation loses.
	@Test
	void aVariousArtistsCompilationLosesAgainstTheArtistAlbumEvenIfOlder() {
		Track t = track("01.mp3");
		Release compilation = new Release("compilation", ReleaseType.ALBUM, ReleaseDate.yearOnly(2000), List.of("Various Artists"), true);
		Release original = new Release("original", ReleaseType.ALBUM, ReleaseDate.exact(2001, 9, 11), List.of("Nickelback"), false);

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprintsWithArtist("Nickelback", "rec"), releases(Map.of("rec", List.of(compilation, original))));

		assertThat(candidacy.candidateReleases()).containsExactlyInAnyOrder(compilation, original);
		assertThat(candidacy.selectedRelease()).contains(original);
	}

	// Real case: a Bon Jovi recording appears only on the "Cross Road" compilation (same artist, but
	// a Compilation); another recording of the same cluster is on the album "Keep the Faith". The
	// exclusion applies to the union, so the album wins.
	@Test
	void theCompilationExclusionAppliesToTheUnionOfTheRecordings() {
		Track t = track("01.mp3");
		Release crossRoad = new Release("cross-road", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994), List.of("Bon Jovi"), true);
		Release keepTheFaith = new Release("keep-the-faith", ReleaseType.ALBUM, ReleaseDate.exact(1992, 11, 3), List.of("Bon Jovi"), false);
		Release olderCompilation = new Release("compilation-1990", ReleaseType.ALBUM, ReleaseDate.yearOnly(1990), List.of("Bon Jovi"), true);

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprintsWithArtist("Bon Jovi", "rec-1", "rec-2"), releases(Map.of("rec-1", List.of(crossRoad, olderCompilation), "rec-2", List.of(keepTheFaith))));

		assertThat(candidacy.selectedRelease()).contains(keepTheFaith);
	}

	@Test
	void aReleaseCreditedToAnotherArtistLosesAgainstTheOneOfTheRecordingArtist() {
		Track t = track("01.mp3");
		Release others = new Release("others", ReleaseType.ALBUM, ReleaseDate.yearOnly(1981), List.of("Queen"), false);
		Release own = new Release("own", ReleaseType.ALBUM, ReleaseDate.yearOnly(1997), List.of("Foo Fighters"), false);

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprintsWithArtist("Foo Fighters", "rec"), releases(Map.of("rec", List.of(others, own))));

		assertThat(candidacy.selectedRelease()).contains(own);
	}

	@Test
	void ifOnlyCompilationsRemainTheChoiceIsMadeAmongThem() {
		Track t = track("01.mp3");
		Release compilation = new Release("compilation", ReleaseType.ALBUM, ReleaseDate.yearOnly(1994), List.of("Bon Jovi"), true);

		Tier2Candidacy candidacy = Tier2Identifier.identify(
		        t, fingerprintsWithArtist("Bon Jovi", "rec"), releases(Map.of("rec", List.of(compilation))));

		assertThat(candidacy.selectedRelease()).contains(compilation);
	}

	@Test
	void withAKnownTitleTheReleaseWhereTheTrackCarriesThatTitleWins() {
		var metadata = new TrackMetadata("Linkin Park", "In The End", null, null, null, null, null, ExternalIds.UNKNOWN);
		Track t = new Track(Path.of("/dest/08.mp3"), metadata, new Provenance(Path.of("/music/Linkin Park/08 - In The End.mp3"), ProvenanceState.INTACT));
		Release demo = new Release("demo", ReleaseType.ALBUM, ReleaseDate.yearOnly(1999), List.of("Linkin Park"), false, new ReleaseInfo("Hybrid Theory", "Linkin Park", "rec", 1, 7, "The Untitled"));
		Release album = new Release("album", ReleaseType.ALBUM, ReleaseDate.exact(2000, 10, 24), List.of("Linkin Park"), false, new ReleaseInfo("Hybrid Theory", "Linkin Park", "rec", 1, 8, "In the End"));

		Tier2Candidacy candidacy = Tier2Identifier.identify(t, fingerprintsWithArtist("Linkin Park", "rec"), releases(Map.of("rec", List.of(demo, album))));

		assertThat(candidacy.selectedRelease()).contains(album);
	}
}
