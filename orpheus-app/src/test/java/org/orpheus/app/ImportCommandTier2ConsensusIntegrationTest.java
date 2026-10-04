package org.orpheus.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.app.identification.FakeTier2AdaptersProfile;
import org.orpheus.app.identification.GenresFake;
import org.orpheus.app.identification.ReleaseGroupSearchFake;
import org.orpheus.app.identification.ReleaseLookupFake;
import org.orpheus.app.identification.FingerprintLookupFake;
import org.orpheus.cli.ImportCommand;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.port.ReleaseGroupSearchPort;
import org.orpheus.persistence.entity.LedgerEntryEntity;
import org.orpheus.persistence.entity.ReviewItemEntity;

/**
 * Checks the real orchestration of the multi-track consensus (ADR-001/SPEC 4.5) and of the individual
 * Tier 2 identification (SPEC 4.4) inside {@code ImportCommand}, with the Tier 2 ports replaced by
 * deterministic fakes ({@link FakeTier2AdaptersProfile}): no network call, the same "real objects" style
 * as {@link ImportCommandIntegrationTest}. The assertions query by the source path specific to the test,
 * not by absolute count: the tests share the same Quarkus datasource.
 */
@QuarkusTest
@TestProfile(FakeTier2AdaptersProfile.class)
class ImportCommandTier2ConsensusIntegrationTest {

	@Inject
	@TopCommand
	ImportCommand command;

	@Inject
	FingerprintLookupFake fingerprints;

	@Inject
	ReleaseLookupFake releases;

	@Inject
	GenresFake genres;

	@Inject
	ReleaseGroupSearchFake releaseGroupSearch;

	@BeforeEach
	void clearFakes() {
		fingerprints.clear();
		releases.clear();
		genres.clear();
		releaseGroupSearch.clear();
	}

	@Test
	void aGroupWithPlaceholderTagsAboveTheThresholdIsForcedOnTier2(@TempDir Path source, @TempDir Path destination) throws Exception {
		// A subfolder, not the root of the source: the files lying in the root never form an album by
		// folder (corrective plan, phase 1).
		Path folder = Files.createDirectories(source.resolve("disc"));
		Path t1 = folder.resolve("01.mp3");
		Path t2 = folder.resolve("02.mp3");
		Path t3 = folder.resolve("03.mp3");
		// Placeholder tags: no artist, no album, which is exactly the gate of the consensus (SPEC 4.5).
		// Common folder -> Tier 0 groups them in a single UNVERIFIED Album.
		createMp3Track(t1, null, "Uno", null, null, null, "1");
		createMp3Track(t2, null, "Due", null, null, null, "2");
		createMp3Track(t3, null, "Tre", null, null, null, "3");

		Release release = new Release("release-real", ReleaseType.ALBUM, ReleaseDate.yearOnly(1999));
		fingerprints.set(t1, List.of("rec"));
		fingerprints.set(t2, List.of("rec"));
		fingerprints.set(t3, List.of("rec"));
		releases.set("rec", List.of(release));

		runImport(source, destination);

		for (Path trackFile : List.of(t1, t2, t3)) {
			List<LedgerEntryEntity> entries = entriesFor(trackFile);
			assertThat(entries).hasSize(1);
			assertThat(entries.get(0).action.name()).isEqualTo("RECONSTRUCTED");
			assertThat(entries.get(0).reason).contains("release-real").contains("forced by the multi-track consensus");

			List<ReviewItemEntity> items = itemsFor(trackFile);
			assertThat(items).hasSize(1);
			assertThat(items.get(0).category.name()).isEqualTo("TIER2_RECONSTRUCTION");
			assertThat(items.get(0).trackMusicBrainzReleaseId).isEqualTo("release-real");
		}
		// The year confirmed by Tier 2 (1999) appears in the name of the year-album folder, not the
		// placeholder year of Tier 0/1 (no year tag written).
		assertThat(Files.walk(destination)
		                .filter(Files::isRegularFile)
		                .map(p -> p.getParent().getFileName().toString()))
		                                                                  .allMatch(
		                                                                          name -> name
		                                                                                      .startsWith(
		                                                                                              "1999 -"));
	}

	@Test
	void aGroupWithPlaceholderTagsBelowTheThresholdIsNotForced(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path t1 = source.resolve("01.mp3");
		Path t2 = source.resolve("02.mp3");
		Path t3 = source.resolve("03.mp3");
		createMp3Track(t1, null, "Uno", null, null, null, "1");
		createMp3Track(t2, null, "Due", null, null, null, "2");
		createMp3Track(t3, null, "Tre", null, null, null, "3");

		// Two different releases, 1/3 each: none reaches 65%. The third finds nothing.
		Release releaseA = new Release("release-a", ReleaseType.ALBUM, ReleaseDate.yearOnly(1990));
		Release releaseB = new Release("release-b", ReleaseType.ALBUM, ReleaseDate.yearOnly(1991));
		fingerprints.set(t1, List.of("rec-a"));
		fingerprints.set(t2, List.of("rec-b"));
		releases.set("rec-a", List.of(releaseA));
		releases.set("rec-b", List.of(releaseB));

		runImport(source, destination);

		List<LedgerEntryEntity> entriesT1 = entriesFor(t1);
		assertThat(entriesT1.get(0).action.name()).isEqualTo("RECONSTRUCTED");
		assertThat(entriesT1.get(0).reason).contains("release-a").contains("individual identification");

		List<LedgerEntryEntity> entriesT2 = entriesFor(t2);
		assertThat(entriesT2.get(0).action.name()).isEqualTo("RECONSTRUCTED");
		assertThat(entriesT2.get(0).reason).contains("release-b").contains("individual identification");

		// No Tier 2 match for t3: Tier 0/1 behavior unchanged, no mention of Tier 2.
		List<LedgerEntryEntity> entriesT3 = entriesFor(t3);
		assertThat(entriesT3.get(0).action.name()).isNotEqualTo("RECONSTRUCTED");
		assertThat(entriesT3.get(0).reason).doesNotContain("Tier 2");
	}

	@Test
	void anIsolatedTrackWithATier2MatchIsReconstructedButStaysUnderUnidentified(
	                                                                            @TempDir Path source, @TempDir Path destination) throws Exception {
		Path isolated = source.resolve("lone.mp3");
		createMp3Track(isolated, "Lone Artist", "Lone Title", null, null, null, null);

		Release release = new Release("release-isolated", ReleaseType.ALBUM, ReleaseDate.exact(1985, 3, 1));
		fingerprints.set(isolated, List.of("rec-isolated"), "Lone Artist");
		releases.set("rec-isolated", List.of(release));

		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(isolated);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action.name()).isEqualTo("RECONSTRUCTED");
		assertThat(entries.get(0).reason).contains("release-isolated");
		// The Release of this test carries no title: the isolated path stays the existing one under
		// "Unidentified", not a "year - album title" folder built from scratch.
		assertThat(entries.get(0).destination).contains("Unidentified");

		List<ReviewItemEntity> items = itemsFor(isolated);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).category.name()).isEqualTo("TIER2_RECONSTRUCTION");
		assertThat(items.get(0).trackMusicBrainzReleaseId).isEqualTo("release-isolated");
	}

	// ADR-0018 point C: "Beatles/ - Sugar Sugar.mp3" whose fingerprint contains only The Archies. The
	// consistent cluster wins over the tag, but the contrast must stay visible in ledger and queue.
	@Test
	void aFingerprintContrastingTheTagIsAppliedAndDeclared(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path file = source.resolve("sugar.mp3");
		createMp3Track(file, "Beatles", "Sugar Sugar", null, null, null, null);

		Release release = new Release("release-archies", ReleaseType.ALBUM, ReleaseDate.yearOnly(1969), List.of("The Archies"), false);
		fingerprints.set(file, List.of("rec-archies"), "The Archies", "Sugar, Sugar");
		releases.set("rec-archies", List.of(release));

		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(file);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action.name()).isEqualTo("RECONSTRUCTED");
		assertThat(entries.get(0).reason).contains("release-archies")
		                                 .contains("contrasting the tag artist \"Beatles\"")
		                                 .contains("\"The Archies\"");
		List<ReviewItemEntity> items = itemsFor(file);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).reason).contains("contrasting");
	}

	// ADR-0021: the copy receives the tags of the release, the genre of the release group and the kept
	// original artist; the source stays intact and the correction ends up in the review queue.
	@Test
	void theCopyReceivesTheReleaseTagsAndTheGenreAndTheCorrectionIsReported(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path file = source.resolve("sugar.mp3");
		createMp3Track(file, "Beatles", "Sugar Sugar", null, null, null, null);
		byte[] sourceBefore = Files.readAllBytes(file);

		ReleaseInfo info = new ReleaseInfo("Everything's Archie", "The Archies", "rec-archies", 1, 7, "Sugar, Sugar", "rg-archies", "The Archies", List.of("art-archies"), List.of("art-archies"));
		Release release = new Release("release-archies", ReleaseType.ALBUM, ReleaseDate.yearOnly(1969), List.of("The Archies"), false, info);
		fingerprints.set(file, List.of("rec-archies"), "The Archies", "Sugar, Sugar");
		releases.set("rec-archies", List.of(release));
		genres.set("rg-archies", List.of("bubblegum pop", "pop"));

		runImport(source, destination);

		Path copy = Files.walk(destination).filter(Files::isRegularFile).findFirst().orElseThrow();
		Tag tag = AudioFileIO.read(copy.toFile()).getTag();
		assertThat(tag.getFirst(FieldKey.ARTIST)).isEqualTo("The Archies");
		assertThat(tag.getFirst(FieldKey.TITLE)).isEqualTo("Sugar, Sugar");
		assertThat(tag.getFirst(FieldKey.ALBUM)).isEqualTo("Everything's Archie");
		assertThat(tag.getFirst(FieldKey.YEAR)).isEqualTo("1969");
		assertThat(tag.getAll(FieldKey.GENRE)).containsExactly("Bubblegum Pop", "Pop");
		assertThat(tag.getFirst(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID)).isEqualTo("rg-archies");
		assertThat(Files.readAllBytes(file)).isEqualTo(sourceBefore);
		assertThat(itemsFor(file)).filteredOn(s -> s.category.name().equals("TAG_CORRECTION"))
		                          .singleElement()
		                          .satisfies(s -> assertThat(s.reason).contains("ARTIST: Beatles -> The Archies"));
	}

	// ADR-0021, revision of 2026-09-27: an already ordered and never identified album takes the genres
	// from the release group with identical artist and title, and every track the year of the folder.
	@Test
	void anOrderedAlbumTakesTheGenresByNameAndTheYearOfTheFolder(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path folder = Files.createDirectories(source.resolve("Deep Purple").resolve("Burn"));
		createMp3Track(folder.resolve("01.mp3"), "Deep purple", "Burn", "BURN", null, "1974", "1");
		createMp3Track(folder.resolve("02.mp3"), "Deep purple", "Might Just Take Your Life", "BURN", null, "1974", "2");
		createMp3Track(folder.resolve("03.mp3"), "Deep purple", "Lay Down, Stay Down", "BURN", null, "1973", "3");
		releaseGroupSearch.set("Deep purple", "BURN", List.of(new ReleaseGroupSearchPort.FoundReleaseGroup("rg-burn-ep", "Burn", "Deep Purple", List.of("id-dp"), "EP", List.of()), new ReleaseGroupSearchPort.FoundReleaseGroup("rg-burn", "Burn", "Deep Purple", List.of("id-dp"), "Album", List.of())));
		genres.set("rg-burn", List.of("hard rock", "rock"));

		runImport(source, destination);

		List<Path> copies = Files.walk(destination).filter(Files::isRegularFile).toList();
		assertThat(copies).hasSize(3).allSatisfy(p -> {
			assertThat(p.getParent().getFileName().toString()).isEqualTo("1974 - Burn");
			// The MusicBrainz spelling found by the search by name counts as the canonical name.
			assertThat(p.getParent().getParent().getFileName().toString()).isEqualTo("Deep Purple");
			Tag tag = AudioFileIO.read(p.toFile()).getTag();
			assertThat(tag.getAll(FieldKey.GENRE)).containsExactly("Hard Rock", "Rock");
			assertThat(tag.getFirst(FieldKey.YEAR)).isEqualTo("1974");
			assertThat(tag.getFirst(FieldKey.ALBUM_ARTIST)).isEqualTo("Deep Purple");
			assertThat(tag.getFirst(FieldKey.ARTIST)).isEqualTo("Deep Purple");
			// The album title also takes the MusicBrainz spelling, in the folder and in the tags.
			assertThat(tag.getFirst(FieldKey.ALBUM)).isEqualTo("Burn");
		});
	}

	@Test
	void aGroupWithRealArtistsAndPlaceholderAlbumIsNotEligibleForTheConsensus(@TempDir Path source, @TempDir Path destination) throws Exception {
		// The CD1/CD2 case of SPEC 4.5: real (consistent) artist tags, only the album was never written: a
		// choice already made by the user, not an ambiguity for the consensus (SPEC 4.5), even though it
		// stays a candidate for the individual Tier 2 per track (SPEC 4.4, album tag still a
		// placeholder). Both tracks deliberately converge on the same release: if the eligibility gate of
		// the consensus were broken and the group treated as eligible, the 2/2 = 100% count would exceed
		// the threshold and the reasons would say "forced by the multi-track consensus" instead of
		// "individual identification": this test would fail silently looking only at the action or the
		// release, not at the exact wording.
		Path t1 = source.resolve("01.mp3");
		Path t2 = source.resolve("02.mp3");
		createMp3Track(t1, "Same Artist", "Uno", null, null, null, "1");
		createMp3Track(t2, "Same Artist", "Due", null, null, null, "2");

		Release release = new Release("release-shared", ReleaseType.ALBUM, ReleaseDate.yearOnly(2001));
		fingerprints.set(t1, List.of("rec"), "Same Artist");
		fingerprints.set(t2, List.of("rec"), "Same Artist");
		releases.set("rec", List.of(release));

		runImport(source, destination);

		for (Path trackFile : List.of(t1, t2)) {
			List<LedgerEntryEntity> entries = entriesFor(trackFile);
			assertThat(entries).hasSize(1);
			assertThat(entries.get(0).action.name()).isEqualTo("RECONSTRUCTED");
			assertThat(entries.get(0).reason).contains("release-shared")
			                                 .contains("individual identification")
			                                 .doesNotContain(
			                                         "forced by the multi-track consensus");
		}
	}

	private void runImport(Path source, Path destination) throws IOException {
		command.source = source;
		command.destination = destination;
		command.musicbrainzUserAgent = null;
		command.call();
	}

	private static List<LedgerEntryEntity> entriesFor(Path source) {
		return LedgerEntryEntity.list("source", source.toString());
	}

	private static List<ReviewItemEntity> itemsFor(Path source) {
		return ReviewItemEntity.list("trackPhysicalPath", source.toString());
	}

	private static void createMp3Track(
	                                   Path destination, String artist, String title, String album, String albumArtist, String year, String track) throws Exception {
		generateSilentMp3(destination);
		AudioFile audioFile = AudioFileIO.read(destination.toFile());
		Tag tag = audioFile.getTagOrCreateAndSetDefault();
		if (artist != null) {
			tag.setField(FieldKey.ARTIST, artist);
		}
		if (title != null) {
			tag.setField(FieldKey.TITLE, title);
		}
		if (album != null) {
			tag.setField(FieldKey.ALBUM, album);
		}
		if (albumArtist != null) {
			tag.setField(FieldKey.ALBUM_ARTIST, albumArtist);
		}
		if (year != null) {
			tag.setField(FieldKey.YEAR, year);
		}
		if (track != null) {
			tag.setField(FieldKey.TRACK, track);
		}
		AudioFileIO.write(audioFile);
	}

	private static void generateSilentMp3(Path destination) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(
		        "ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", "0.2", "-codec:a", "libmp3lame", destination
		                                                                                                                          .toString())
		                                                                                                                                      .redirectErrorStream(true)
		                                                                                                                                      .start();
		if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
			throw new IOException("ffmpeg did not generate the test file: " + destination);
		}
	}
}
