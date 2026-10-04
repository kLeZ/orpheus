package org.orpheus.domain.identification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

// Fixtures derived from the real AcoustID answers collected on 2026-09-26 for the tracks of ~/Musica
// that Tier 2 had assigned to releases by other artists (ADR-0018).
class CandidateRecordingFilterTest {

	private static Track track(String artist) {
		var metadata = new TrackMetadata(artist, null, null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/music/t.mp3"), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/t.mp3"), metadata, provenance);
	}

	private static CandidateRecording rec(String id, String title, String artist, Integer duration) {
		return new CandidateRecording(id, title, List.of(artist), Optional.ofNullable(duration));
	}

	// Bon Jovi "Bed of Roses", a 393 s file: 10 Bon Jovi recordings, plus Andy Williams (167 s) and a
	// baroque Canon (391 s, compatible duration: only the artist constraint excludes it).
	private static FingerprintLookupResult bedOfRoses() {
		List<CandidateRecording> candidates = new ArrayList<>();
		candidates.add(rec("bj-1", "Bed of Roses", "Bon Jovi", 394));
		candidates.add(rec("bj-acoustic", "Bed of Roses (acoustic)", "Bon Jovi", 259));
		candidates.add(rec("canon", "Canon and gigue in D major", "English Chamber Orchestra", 391));
		for (int i = 2; i <= 9; i++) {
			candidates.add(rec("bj-" + i, "Bed of Roses", "Bon Jovi", 394));
		}
		candidates.add(rec("andy", "Days of Wine and Roses", "Andy Williams", 167));
		return new FingerprintLookupResult(Optional.of(393), candidates);
	}

	@Test
	void withKnownArtistRecordingsOfOtherArtistsAreExcludedEvenWithCompatibleDuration() {
		var result = CandidateRecordingFilter.filter(track("Bon Jovi"), bedOfRoses());

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId)
		                              .doesNotContain("canon", "andy", "bj-acoustic")
		                              .hasSize(9);
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	@Test
	void withUnknownArtistTheDominantIdentityWinsAndDurationExcludesTheAcousticVersion() {
		var result = CandidateRecordingFilter.filter(track(null), bedOfRoses());

		assertThat(result.survivors()).extracting(CandidateRecording::title).containsOnly("Bed of Roses");
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	@Test
	void aPlaceholderArtistIsTreatedAsUnknown() {
		var result = CandidateRecordingFilter.filter(track("Artista Sconosciuto"), bedOfRoses());

		assertThat(result.survivors()).extracting(CandidateRecording::title).containsOnly("Bed of Roses");
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	// Foo Fighters "Monkey Wrench", 231 s: the artist constraint alone would let another song of the
	// same band through; the duration excludes it.
	@Test
	void theDurationExcludesAnotherSongOfTheSameArtist() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(231), List.of(rec("mw", "Monkey Wrench", "Foo Fighters", 232), rec("lonely", "Lonely as You", "Foo Fighters", 277), rec("queen", "Under Pressure", "Queen, David Bowie", 243)));

		var result = CandidateRecordingFilter.filter(track("Foo Fighters"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("mw");
	}

	// "Kung Fu Fighting" tagged Foo Fighters: 27 recordings out of 37 by Carl Douglas, a single one by
	// the Foo Fighters. The tag wins because the cluster contains the tag artist.
	@Test
	void ifTheClusterContainsTheTagArtistItWinsEvenInMinority() {
		List<CandidateRecording> candidates = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			candidates.add(rec("cd-" + i, "Kung Fu Fighting", "Carl Douglas", 197));
		}
		candidates.add(rec("ff", "Kung Fu Fightin", "Foo Fighters", 197));

		var result = CandidateRecordingFilter.filter(track("Foo Fighters"), new FingerprintLookupResult(Optional.of(197), candidates));

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("ff");
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	// "Beatles/ - Sugar Sugar.mp3": the cluster contains only The Archies. A file mislabeled at the
	// origin: the consistent fingerprint wins, declaring the contrast (ADR-0018 point C).
	@Test
	void aConsistentClusterWithoutTheTagArtistWinsDeclaringTheContrast() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(168), List.of(rec("a1", "Sugar, Sugar", "The Archies", 167), rec("a2", "Sugar Sugar", "The Archies", 168), rec("a3", "Sugar, Sugar", "The Archies", null)));

		var result = CandidateRecordingFilter.filter(track("Beatles"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("a1", "a2", "a3");
		assertThat(result.artistContrastingTag()).contains("The Archies");
	}

	// "Ray Charles/ - When A Man Loves a Woman.mp3": a cluster spread over four performers, none of
	// them the one of the tag. No identification.
	@Test
	void aScatteredClusterWithoutTheTagArtistIdentifiesNothing() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(200), List.of(rec("1", "When a Man Loves a Woman", "Percy Sledge", 200), rec("2", "When a Man Loves a Woman", "Bee Gees", 201), rec("3", "When a Man Loves a Woman", "Bread", 199), rec("4", "When a Man Loves a Woman", "James Last", 198)));

		var result = CandidateRecordingFilter.filter(track("Ray Charles"), lookup);

		assertThat(result.survivors()).isEmpty();
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	// "Blind Guardian/ - Paradise Lost Gothic.mp3": one Paradise Lost recording and one by another
	// artist. A tie is not enough to override the tag.
	@Test
	void againstAKnownTagATieDoesNotOverrideTheTag() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(300), List.of(rec("pl", "Gothic", "Paradise Lost", 300), rec("x", "Other", "Other Artist", 301)));

		var result = CandidateRecordingFilter.filter(track("Blind Guardian"), lookup);

		assertThat(result.survivors()).isEmpty();
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	@Test
	void withoutArtistATieIsEnoughAndTheFirstGroupWins() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(300), List.of(rec("pl", "Gothic", "Paradise Lost", 300), rec("x", "Other", "Other Artist", 301)));

		var result = CandidateRecordingFilter.filter(track(null), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("pl");
	}

	@Test
	void againstAKnownTagCandidatesWithoutMetadataFormNoIdentity() {
		var result = CandidateRecordingFilter.filter(track("Bon Jovi"), FingerprintLookupResult.idsOnly(List.of("x", "y")));

		assertThat(result.survivors()).isEmpty();
	}

	@Test
	void withoutArtistIdOnlyCandidatesSurvive() {
		var result = CandidateRecordingFilter.filter(track(null), FingerprintLookupResult.idsOnly(List.of("x")));

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("x");
	}

	@Test
	void aDurationWithinToleranceSurvivesBeyondItDoesNot() {
		int limit = CandidateRecordingFilter.DURATION_TOLERANCE_SECONDS;
		var lookup = new FingerprintLookupResult(
		        Optional.of(200), List.of(rec("inside", "T", "A", 200 + limit), rec("outside", "T", "A", 200 + limit + 1)));

		var result = CandidateRecordingFilter.filter(track("A"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("inside");
	}

	// "It's My Life" (224 s): among the Bon Jovi recordings with a compatible duration there are other
	// songs of the same band; the title with a strict majority excludes them.
	@Test
	void amongTheArtistRecordingsTheTitleWithAStrictMajorityWins() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(224), List.of(rec("i1", "It\u2019s My Life", "Bon Jovi", 224), rec("i2", "It's My Life", "Bon Jovi", 227), rec("i3", "It's My Life", "Bon Jovi", null), rec("other", "All About Lovin' You", "Bon Jovi", 226)));

		var result = CandidateRecordingFilter.filter(track("Bon Jovi"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("i1", "i2", "i3");
	}

	@Test
	void amongTheArtistRecordingsATitleTieKeepsThemAll() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(200), List.of(rec("a", "Break the Line", "Guano Apes", 200), rec("b", "Break the Line (video edit)", "Guano Apes", 199)));

		var result = CandidateRecordingFilter.filter(track("Guano Apes"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("a", "b");
	}

	@Test
	void noCandidateNoSurvivor() {
		var result = CandidateRecordingFilter.filter(track("A"), FingerprintLookupResult.empty());

		assertThat(result.survivors()).isEmpty();
	}

	private static Track trackWithTitle(String artist, String title) {
		var metadata = new TrackMetadata(artist, title, null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/music/t.mp3"), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/t.mp3"), metadata, provenance);
	}

	// "Linkin Park/08 - In The End.mp3" had ended up as "The Untitled" on the real run.
	@Test
	void withAKnownTitleTheArtistRecordingsWithThatTitleWin() {
		var lookup = new FingerprintLookupResult(
		        Optional.of(216), List.of(rec("u1", "The Untitled", "Linkin Park", 216), rec("u2", "The Untitled", "Linkin Park", 216), rec("e", "In the End", "Linkin Park", 216)));

		var result = CandidateRecordingFilter.filter(trackWithTitle("Linkin Park", "In The End"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("e");
	}

	@Test
	void aContrastWithTheTagRequiresAMatchingTitle() {
		var lookup = new FingerprintLookupResult(Optional.of(200), List.of(rec("mi", "Mission Impossible", "Dream Theater", 200)));

		assertThat(CandidateRecordingFilter.filter(trackWithTitle("Lagwagon", "Take a look around - Punk cover"), lookup)
		                                   .survivors()).isEmpty();
		assertThat(CandidateRecordingFilter.filter(trackWithTitle("Beatles", "Sugar Sugar"), new FingerprintLookupResult(Optional.of(168), List.of(rec("a", "Sugar, Sugar", "The Archies", 167))))
		                                   .artistContrastingTag()).contains("The Archies");
	}

	private static Track track(String artist, String title, String artistFromPath) {
		var metadata = new TrackMetadata(artist, title, null, null, null, null, null, ExternalIds.UNKNOWN);
		var provenance = new Provenance(Path.of("/music/t.mp3"), ProvenanceState.INTACT);
		return new Track(Path.of("/dest/t.mp3"), metadata, provenance, artistFromPath);
	}

	// "Skunk Anansie/ - Because of you.mp3", without tags: three title variants, no identity majority,
	// but the folder tells the artist (real run of 2026-10-04).
	@Test
	void withoutTagsTheFolderChoosesTheRecordingsWithoutDeclaringAContrast() {
		var lookup = new FingerprintLookupResult(Optional.of(221), List.of(rec("a", "Because of You", "Skunk Anansie", 219), rec("b", "Because of You (acoustic version @ 3FM Holland)", "Skunk Anansie", 217), rec("c", "Because of You (original)", "Skunk Anansie", 219)));

		var result = CandidateRecordingFilter.filter(track(null, "Because of you", "Skunk Anansie"), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("a", "c");
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	// "Ronnie James Dio/ - Holy Diver.mp3": the recording credited to Ronnie James Dio is only on an
	// undated bootleg; the rest of the cluster is "Holy Diver" by Dio and stays as the fallback.
	@Test
	void aFallbackFromTheRestOfTheClusterWithItsContrast() {
		List<CandidateRecording> candidates = new ArrayList<>(List.of(rec("rjd", "Holy Diver", "Ronnie James Dio", 349)));
		for (int i = 0; i < 5; i++) {
			candidates.add(rec("dio-" + i, "Holy Diver", "Dio", 352));
		}

		var result = CandidateRecordingFilter.filter(track("Ronnie James Dio", "Holy Diver", null), new FingerprintLookupResult(Optional.of(349), candidates));

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("rjd");
		assertThat(result.fallback()).hasValueSatisfying(r -> {
			assertThat(r.survivors()).hasSize(5);
			assertThat(r.artistContrastingTag()).hasValue("Dio");
		});
	}

	// With a known title the count is by artist: two title variants of the Desert Sessions no longer
	// split the majority, nor do two credits of Bill Haley.
	@Test
	void withAKnownTitleTheMajorityIsCountedByArtist() {
		var drug = new FingerprintLookupResult(Optional.of(144), List.of(rec("a", "Like a Drug", "The Desert Sessions", 144), rec("b", "Like a Drug (instrumental)", "The Desert Sessions", 145)));
		var result = CandidateRecordingFilter.filter(track("Queens of the Stone Age", "Like a drug", null), drug);
		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("a");
		assertThat(result.artistContrastingTag()).hasValue("The Desert Sessions");

		var clock = new FingerprintLookupResult(Optional.of(130), List.of(rec("a", "Rock Around the Clock", "Bill Haley", 130), rec("b", "Rock Around the Clock", "Bill Haley & His Comets", 132), rec("c", "(We're Gonna) Rock Around the Clock", "Bill Haley & His Comets", 124), rec("d", "Rock Around the Clock", "Bill Haley, The Comets", 133)));
		assertThat(CandidateRecordingFilter.filter(track("Elvis Presley", "Rock Around The Clock", null), clock)
		                                   .survivors()).extracting(CandidateRecording::recordingId)
		                                                .containsExactly("a", "b", "c", "d");
	}

	// "Blind Guardian/ - The Bard's Song.mp3": the album recording carries a subtitle.
	@Test
	void aSubtitleIsNotAVersion() {
		var lookup = new FingerprintLookupResult(Optional.of(206), List.of(rec("album", "The Bard's Song (In the Forest)", "Blind Guardian", 206), rec("dvd", "The Bard's Song", "Blind Guardian", 206), rec("live", "The Bard's Song (live)", "Blind Guardian", 210)));

		var result = CandidateRecordingFilter.filter(track("Blind Guardian", "The Bard's Song", null), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId)
		                              .containsExactlyInAnyOrder("album", "dvd");
	}

	@Test
	void theParenthesesOfTheLocalTitleDoNotPreventTheComparison() {
		var lookup = new FingerprintLookupResult(Optional.of(104), List.of(rec("a", "Surfin' USA", "Pennywise", 104), rec("b", "Surfin' USA", "Pennywise", 113)));

		var result = CandidateRecordingFilter.filter(track("Millencolin", "Surfing USA (Punk Cover)", null), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("a", "b");
		assertThat(result.artistContrastingTag()).hasValue("Pennywise");
	}

	// "George Thorogood" in the tag, "George Thorogood & the Destroyers" on the 1982 album.
	@Test
	void aCreditExtendingTheTagArtistMatches() {
		var lookup = new FingerprintLookupResult(Optional.of(290), List.of(rec("sampler", "Bad to the Bone", "George Thorogood", 290), rec("album", "Bad to the Bone", "George Thorogood & the Destroyers", 292)));

		var result = CandidateRecordingFilter.filter(track("George Thorogood", "Bad to the Bone", null), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId)
		                              .containsExactly("sampler", "album");
		assertThat(result.artistContrastingTag()).isEmpty();
	}

	@Test
	void aPrefixWithoutWordBoundaryIsNotTheSameArtist() {
		var lookup = new FingerprintLookupResult(Optional.of(200), List.of(rec("queen", "Go With the Flow", "Queen", 200), rec("qotsa", "Go With the Flow", "Queens of the Stone Age", 200), rec("qotsa2", "Go With the Flow", "Queens of the Stone Age", 201)));

		var result = CandidateRecordingFilter.filter(track("Queen", "Go With the Flow", null), lookup);

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("queen");
	}

	// "Nirvana/ - Sliver.mp3": studio 2297 AcoustID associations, live at the Paramount 2.
	@Test
	void aRecordingWithIsolatedAssociationsIsDropped() {
		var studio = new CandidateRecording("studio", "Sliver", List.of("Nirvana"), Optional.of(134), Optional.of(0.98), Optional.of(2297));
		var live = new CandidateRecording("live", "Sliver", List.of("Nirvana"), Optional.of(131), Optional.of(0.98), Optional.of(2));
		var poco = new CandidateRecording("rare", "Sliver", List.of("Nirvana"), Optional.of(134), Optional.of(0.98), Optional.of(30));

		var result = CandidateRecordingFilter.filter(track("Nirvana", "Sliver", null), new FingerprintLookupResult(Optional.of(134), List.of(studio, live, poco)));

		assertThat(result.survivors()).extracting(CandidateRecording::recordingId).containsExactly("studio", "rare");
	}
}
