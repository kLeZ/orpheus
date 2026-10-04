package org.orpheus.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.ingest.TagsToWrite.Correction;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.Track;

// One check per rule of ADR-0021, on the cases of the real run.
class TagRulesTest {

	private static TrackMetadata m(@Nullable String artist, @Nullable String title, @Nullable String album, @Nullable String albumArtist, @Nullable Integer year, @Nullable Integer number) {
		return new TrackMetadata(artist, title, album, albumArtist, year, number, null, ExternalIds.UNKNOWN);
	}

	private static Track track(TrackMetadata originals, TrackMetadata current, List<String> genres, @Nullable String parent) {
		Path p = Path.of("/music/x.mp3");
		return new Track(p, current, new Provenance(p, ProvenanceState.INTACT), parent, originals, genres);
	}

	private static Release release(String title, String credit, @Nullable String trackCredit, int year, Integer disc, Integer number, String trackTitle) {
		ReleaseInfo info = new ReleaseInfo(title, credit, "rec-1", disc, number, trackTitle, "rg-1", trackCredit, List.of("art-track"), List.of("art-release"));
		return new Release("rel-1", ReleaseType.ALBUM, ReleaseDate.yearOnly(year), List.of(credit), false, info);
	}

	// T01, T24, T26: "Beatles/ - Sugar Sugar.mp3" placed under The Archies.
	@Test
	void aContrastWithTheTagWritesTheIdentifiedArtistAndKeepsTheOriginal() {
		TrackMetadata orig = m("Beatles", null, null, null, null, null);
		Track t = track(orig, m("Beatles", "Sugar Sugar", null, null, null, null), List.of(), "Beatles");
		Release e = release("Everything's Archie", "The Archies", "The Archies", 1969, 1, 7, "Sugar, Sugar");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(e), false, false, "The Archies", null, null, List.of()));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("The Archies");
		assertThat(r.values().get(TagField.TITLE)).containsExactly("Sugar, Sugar");
		assertThat(r.values().get(TagField.ALBUM)).containsExactly("Everything's Archie");
		assertThat(r.values().get(TagField.DATE)).containsExactly("1969");
		assertThat(r.values().get(TagField.TRACKNUMBER)).containsExactly("7");
		assertThat(r.values()).doesNotContainKey(TagField.DISCNUMBER);
		assertThat(r.originals().get(TagField.ARTIST)).containsExactly("Beatles");
		assertThat(r.corrections()).containsExactly(new Correction(TagField.ARTIST, List.of("Beatles"), List.of("The Archies")));
	}

	// T04: a different but equivalent spelling is still a correction ("In The End" -> "In the End").
	@Test
	void theTracklistTitleReplacesTheTagOne() {
		Track t = track(m("Linkin Park", "In The End", null, null, null, 8), m("Linkin Park", "In The End", null, null, null, 8), List.of(), "Linkin Park");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(release("Hybrid Theory", "Linkin Park", "Linkin Park", 2000, 1, 8, "In the End")), false, false, "Linkin Park", null, null, List.of()));

		assertThat(r.corrections()).extracting(Correction::field).containsExactly(TagField.TITLE);
	}

	// T19-T23: MusicBrainz identifiers; T15 only for releases with several discs.
	@Test
	void musicBrainzIdentifiersAndDiscIfMultiDisc() {
		Track t = track(m("Queen", null, null, null, null, null), m("Queen", null, null, null, null, null), List.of(), null);

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(release("Live Killers", "Queen", "Queen", 1979, 2, 7, "We Will Rock You")), false, true, "Queen", null, null, List.of()));

		assertThat(r.values().get(TagField.DISCNUMBER)).containsExactly("2");
		assertThat(r.values().get(TagField.MUSICBRAINZ_TRACKID)).containsExactly("rec-1");
		assertThat(r.values().get(TagField.MUSICBRAINZ_ALBUMID)).containsExactly("rel-1");
		assertThat(r.values().get(TagField.MUSICBRAINZ_ARTISTID)).containsExactly("art-track");
		assertThat(r.values().get(TagField.MUSICBRAINZ_ALBUMARTISTID)).containsExactly("art-release");
		assertThat(r.values().get(TagField.MUSICBRAINZ_RELEASEGROUPID)).containsExactly("rg-1");
	}

	// T08, T17: owned compilation (consensus on a Various Artists release).
	@Test
	void anOwnedCompilationHasVariousArtistsAsAlbumArtistAndTheFlag() {
		Track t = track(m(null, null, null, null, null, null), m(null, null, null, null, null, null), List.of(), null);

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(release("A Tribute to the Scorpions", "Various Artists", "Sonata Arctica", 2000, 1, 3, "Still Loving You")), true, false, "Various Artists", null, null, List.of()));

		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("Various Artists");
		assertThat(r.values().get(TagField.COMPILATION)).containsExactly("1");
		assertThat(r.values().get(TagField.ARTIST)).containsExactly("Sonata Arctica");
	}

	// T05, T14: values from the file name only in empty fields; T02: a tag artist stays.
	@Test
	void anUnidentifiedTrackFillsOnlyTheEmptyFields() {
		TrackMetadata orig = m("Linkin Park", null, null, null, null, null);
		Track t = track(orig, m("Linkin Park", "Crawling", null, null, null, 5), List.of(), "Linkin Park");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Linkin Park", null, null, List.of()));

		assertThat(r.values().get(TagField.TITLE)).containsExactly("Crawling");
		assertThat(r.values().get(TagField.TRACKNUMBER)).containsExactly("5");
		assertThat(r.values()).doesNotContainKey(TagField.ARTIST);
		assertThat(r.corrections()).isEmpty();
	}

	@Test
	void aTitleTakenFromTheFileNameDoesNotReplaceARealTag() {
		TrackMetadata orig = m("Nickelback", "Too Bad", null, null, null, 7);
		Track t = track(orig, orig, List.of(), "Nickelback");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Nickelback", null, null, List.of()));

		assertThat(r.values()).isEmpty();
	}

	// T03: without an artist in the tag, the top-level folder (Skunk Anansie).
	@Test
	void withoutArtistInTheTagTheTopLevelFolder() {
		Track t = track(m(null, null, null, null, null, null), m(null, "Because of you", null, null, null, null), List.of(), "Skunk Anansie");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Skunk Anansie", null, null, List.of()));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("Skunk Anansie");
	}

	// T07, T10, T12 (WRITE since 2026-09-27): album, album artist and year are those of the folder.
	@Test
	void aTier1AlbumWritesAlbumArtistAndYearOfTheFolder() {
		TrackMetadata orig = m("Deep Purple", "Fireball", "Fireball", null, null, 1);
		Track t = track(orig, orig, List.of(), "Deep Purple");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Deep Purple", "Fireball", 1971, List.of()));

		assertThat(r.values().get(TagField.DATE)).containsExactly("1971");
		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("Deep Purple");
		assertThat(r.values().get(TagField.ALBUM)).containsExactly("Fireball");
		assertThat(r.corrections()).isEmpty();
	}

	// "Black Sabbath/1975 - Sabotage": a track with tag 1973 takes the year of the folder.
	@Test
	void aTagYearDifferentFromTheFolderOneIsCorrected() {
		TrackMetadata orig = m("Black Sabbath", "Symptom of the Universe", "Sabotage", null, 1973, 3);
		Track t = track(orig, orig, List.of(), "Black Sabbath");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Black Sabbath", "Sabotage", 1975, List.of()));

		assertThat(r.corrections()).containsExactly(new Correction(TagField.DATE, List.of("1973"), List.of("1975")));
	}

	// "Beatles" under "The Beatles/": the tag artist takes the canonical form of the folder.
	@Test
	void theTagArtistTakesTheCanonicalForm() {
		TrackMetadata orig = m("Beatles", "Help!", "Help!", "Beatles", 1965, 1);
		Track t = track(orig, orig, List.of(), "Beatles");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "The Beatles", "Help!", 1965, List.of(), "The Beatles"));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("The Beatles");
		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("The Beatles");
		assertThat(r.corrections()).extracting(Correction::field)
		                           .containsExactlyInAnyOrder(TagField.ARTIST, TagField.ALBUMARTIST);
	}

	// Typographic characters into ASCII, also in the MusicBrainz values ("blink‐182", U+2010).
	@Test
	void theWrittenValuesAreInAscii() {
		Track t = track(m("Blink 182", "What's my age again", null, null, null, 32), m("Blink 182", "What's my age again", null, null, null, 32), List.of(), "Blink 182");
		Release e = release("Enema of the State", "blink\u2010182", "blink\u2010182", 1999, 1, 6, "What\u2019s My Age Again?");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(e), false, false, "blink\u2010182", null, null, List.of()));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("blink-182");
		assertThat(r.values().get(TagField.TITLE)).containsExactly("What's My Age Again?");
		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("blink-182");
	}

	@Test
	void aFileTextWithTypographicCharactersBecomesAscii() {
		TrackMetadata orig = m("Guns N\u2019 Roses", "Don\u2019t Cry", null, null, null, 4);
		Track t = track(orig, orig, List.of(), null);

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Guns N' Roses", null, null, List.of(), "Guns N\u2019 Roses"));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("Guns N' Roses");
		assertThat(r.values().get(TagField.TITLE)).containsExactly("Don't Cry");
		assertThat(r.originals().get(TagField.TITLE)).containsExactly("Don\u2019t Cry");
	}

	// G01/G02: genre made uniform even if the file has one, original kept; at most 5.
	@Test
	void genresMadeUniformWithTheOriginalKeptAtMostFive() {
		Track t = track(m("Linkin Park", "Crawling", null, null, null, 5), m("Linkin Park", "Crawling", null, null, null, 5), List.of("Nu Metal"), null);

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Linkin Park", null, null, List.of("nu metal", "alternative metal", "rap metal", "alternative rock", "rock", "pop")));

		assertThat(r.values().get(TagField.GENRE))
		                                          .containsExactly("Nu Metal", "Alternative Metal", "Rap Metal", "Alternative Rock", "Rock");
		assertThat(r.originals().get(TagField.GENRE)).containsExactly("Nu Metal");
		assertThat(r.corrections()).extracting(Correction::field).containsExactly(TagField.GENRE);
	}

	@Test
	void aPlaceholderGenreIsNotAnOriginalToKeep() {
		Track t = track(m("Linkin Park", "Crawling", null, null, null, 5), m("Linkin Park", "Crawling", null, null, null, 5), List.of("Other"), null);

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Linkin Park", null, null, List.of("nu metal")));

		assertThat(r.values().get(TagField.GENRE)).containsExactly("Nu Metal");
		assertThat(r.corrections()).isEmpty();
	}

	@Test
	void withoutMusicBrainzGenresTheFileGenreStays() {
		Track t = track(m("kLeZ", "ICE on Fire", null, null, null, null), m("kLeZ", "ICE on Fire", null, null, null, null), List.of("Metalcore"), null);

		assertThat(TagRules.apply(new TagContext(t, Optional.empty(), false, false, "kLeZ", null, null, List.of()))
		                   .values()).doesNotContainKey(TagField.GENRE);
	}

	@Test
	void genreFormat() {
		assertThat(TagRules.formatGenre("hard rock")).isEqualTo("Hard Rock");
		assertThat(TagRules.formatGenre("hip-hop")).isEqualTo("Hip-Hop");
	}

	// "Re-Machined": a Various Artists compilation, "When a Blind Man Cries" played by Metallica.
	@Test
	void aCompilationHasPerformerAndAlbumArtistAndEveryTrackTheComposer() {
		Track t = track(m("Metallica", null, null, null, null, null), m("Metallica", "When a Blind Man Cries", null, null, null, null), List.of(), "Metallica");
		ReleaseInfo info = new ReleaseInfo("Re-Machined: A Tribute to Deep Purple's Machine Head", "Various Artists", "rec-1", 1, 7, "When a Blind Man Cries", "rg-1", "Metallica", List.of("met"), List.of("va"));
		Release e = new Release("rel", ReleaseType.ALBUM, ReleaseDate.yearOnly(2012), List.of("Various Artists"), true, info);
		List<String> authors = List.of("Ritchie Blackmore", "Ian Gillan", "Roger Glover", "Jon Lord", "Ian Paice");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.of(e), true, false, "Various Artists", null, null, List.of(), null, authors));

		assertThat(r.values().get(TagField.ARTIST)).containsExactly("Metallica");
		assertThat(r.values().get(TagField.PERFORMER)).containsExactly("Metallica");
		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("Various Artists");
		assertThat(r.values().get(TagField.COMPOSER)).containsExactlyElementsOf(authors);
	}

	@Test
	void outsideACompilationNoPerformerButComposerYes() {
		Track t = track(m("Deep Purple", "Lazy", "Machine Head", null, 1972, 6), m("Deep Purple", "Lazy", "Machine Head", null, 1972, 6), List.of(), "Deep Purple");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Deep Purple", "Machine Head", 1972, List.of(), "Deep Purple", List.of("Ritchie Blackmore", "Ian Gillan")));

		assertThat(r.values()).doesNotContainKey(TagField.PERFORMER);
		assertThat(r.values().get(TagField.COMPOSER)).containsExactly("Ritchie Blackmore", "Ian Gillan");
	}

	// "Metallica/Re-Machined.../09 - When A Blind Man Cries.mp3": a compilation recognized from the tags.
	@Test
	void aCompilationRecognizedFromTheTagsHasPerformerAndCompilation() {
		Track t = track(m("Metallica", "When A Blind Man Cries", "Re-Machined", null, 2012, 9), m("Metallica", "When A Blind Man Cries", "Re-Machined", null, 2012, 9), List.of(), "Metallica");

		TagsToWrite r = TagRules.apply(new TagContext(t, Optional.empty(), false, false, "Various Artists", "Re-Machined", 2012, List.of(), "Metallica"));

		assertThat(r.values().get(TagField.ALBUMARTIST)).containsExactly("Various Artists");
		assertThat(r.values().get(TagField.PERFORMER)).containsExactly("Metallica");
		assertThat(r.values().get(TagField.COMPILATION)).containsExactly("1");
	}
}
