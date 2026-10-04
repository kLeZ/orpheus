package org.orpheus.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orpheus.cli.ImportCommand;
import org.orpheus.persistence.entity.LedgerEntryEntity;
import org.orpheus.persistence.entity.ReviewItemEntity;

/**
 * Checks the real orchestration of the import command (scan -> tag reading -> Tier 0/1 -> copy ->
 * ledger and review queue), with the real CDI ports (jaudiotagger, SQLite), not mocks. The assertions
 * query by the source path specific to the test, not by absolute count: the tests of this class share
 * the same Quarkus datasource, so rows of other test methods may coexist.
 */
@QuarkusTest
class ImportCommandIntegrationTest {

	@Inject
	@TopCommand
	ImportCommand command;

	@Test
	void twoTracksWithTheSameAlbumTagAreGroupedAndCopied(@TempDir Path source, @TempDir Path destination) throws Exception {
		createMp3Track(source.resolve("01.mp3"), "Artist", "First", "Test Album", "Artist", "2005", "1");
		createMp3Track(source.resolve("02.mp3"), "Artist", "Second", "Test Album", "Artist", "2005", "2");

		runImport(source, destination);

		assertThat(Files.walk(destination).filter(Files::isRegularFile).count()).isEqualTo(2);
		assertThat(entriesFor(source.resolve("01.mp3"))).hasSize(1);
		assertThat(entriesFor(source.resolve("01.mp3")).get(0).action.name()).isEqualTo("IMPORTED_AS_IS");
	}

	@Test
	void anIsolatedTrackIsCopiedAndRecordedInTheReviewQueue(@TempDir Path source, @TempDir Path destination) throws Exception {
		createMp3Track(source.resolve("lone.mp3"), "Lone Artist", "Lone Title", null, null, null, null);

		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(source.resolve("lone.mp3"));
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action.name()).isEqualTo("IMPORTED_AS_IS");

		List<ReviewItemEntity> items = ReviewItemEntity
		                                               .list(
		                                                       "trackPhysicalPath", source
		                                                                                  .resolve("lone.mp3")
		                                                                                  .toString());
		assertThat(items).hasSize(1);
		assertThat(items.get(0).category.name()).isEqualTo("UNIDENTIFIED");
	}

	@Test
	void aCorruptedFileDoesNotBlockTheProcessingOfTheOtherTracks(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path corrupted = source.resolve("corrupted.mp3");
		Files.write(corrupted, new byte[]{
		                                  1,
		                                  2,
		                                  3,
		                                  4
		});
		createMp3Track(source.resolve("valid.mp3"), "Artist", "Title", null, null, null, null);

		runImport(source, destination);

		assertThat(entriesFor(source.resolve("valid.mp3"))).hasSize(1);
		List<ReviewItemEntity> items = ReviewItemEntity
		                                               .list("trackPhysicalPath", corrupted.toString());
		assertThat(items).hasSize(1);
		assertThat(items.get(0).category.name()).isEqualTo("AUDIO_QUALITY");
	}

	@Test
	void rerunningTheSameImportDoesNotOverwriteAndMarksAsDuplicate(@TempDir Path source, @TempDir Path destination) throws Exception {
		createMp3Track(source.resolve("lone.mp3"), "Repeated Artist", "Repeated Title", null, null, null, null);

		runImport(source, destination);
		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(source.resolve("lone.mp3"));
		assertThat(entries).hasSize(2);
		assertThat(entries.get(1).action.name()).isEqualTo("DISCARDED_DUPLICATE");
		assertThat(Files.walk(destination).filter(Files::isRegularFile).count()).isEqualTo(1);
	}

	@Test
	void twoDifferentTracksWithTheSameComputedPathAreBothCopiedNotDiscarded(
	                                                                        @TempDir Path source, @TempDir Path destination) throws Exception {
		// Same artist and title (so the same path computed by DestinationPath) but different audio
		// content: a metadata collision, not a real duplicate. Before the fix, the second track was
		// discarded silently without comparing the content.
		createMp3Track(source.resolve("a.mp3"), "Same Artist", "Same Title", null, null, null, null, 8000);
		createMp3Track(source.resolve("b.mp3"), "Same Artist", "Same Title", null, null, null, null, 11025);

		runImport(source, destination);

		assertThat(Files.walk(destination).filter(Files::isRegularFile).count()).isEqualTo(2);
		assertThat(entriesFor(source.resolve("a.mp3")).get(0).action.name()).isEqualTo("IMPORTED_AS_IS");
		assertThat(entriesFor(source.resolve("b.mp3")).get(0).action.name()).isEqualTo("IMPORTED_AS_IS");
		// ADR-0019: different audio on the same path -> "(version NN)" and a review item.
		assertThat(entriesFor(source.resolve("b.mp3")).get(0).destination).contains("(version 2)");
		assertThat(ReviewItemEntity.<ReviewItemEntity>list("trackPhysicalPath", source.resolve("b.mp3")
		                                                                              .toString())).extracting(e -> e.category.name())
		                                                                                           .contains("ALTERNATIVE_VERSION");
	}

	@Test
	void aDestinationInsideTheSourceIsRejectedWithoutTouchingAnything(@TempDir Path source) throws Exception {
		createMp3Track(source.resolve("01.mp3"), "Artist", "First", "Test Album", "Artist", "2005", "1");
		command.source = source;
		command.destination = source.resolve("organized");

		assertThat(command.call()).isEqualTo(2);
		assertThat(Files.list(source).map(p -> p.getFileName().toString())).containsExactly("01.mp3");
		assertThat(entriesFor(source.resolve("01.mp3"))).isEmpty();
	}

	@Test
	void inADryRunNoFileIsCopiedButTheLedgerIsWritten(@TempDir Path source, @TempDir Path destination) throws Exception {
		createMp3Track(source.resolve("01.mp3"), "Artist", "First", "Test Album", "Artist", "2005", "1");
		createMp3Track(source.resolve("02.mp3"), "Artist", "Second", "Test Album", "Artist", "2005", "2");
		command.dryRun = true;
		try {
			runImport(source, destination);
		} finally {
			command.dryRun = false;
		}

		assertThat(Files.walk(destination).filter(Files::isRegularFile).count()).isZero();
		assertThat(entriesFor(source.resolve("01.mp3"))).hasSize(1);
		assertThat(entriesFor(source.resolve("01.mp3")).get(0).destination).startsWith(destination.toString());
	}

	// ADR-0019: everything goes into the library. The same audio in two folders (the Deep Purple duplicates) -> "(2)".
	@Test
	void twoCopiesWithTheSameAudioBothGoIntoTheLibraryWithACounter(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path a = Files.createDirectories(source.resolve("Burn")).resolve("01.mp3");
		Path b = Files.createDirectories(source.resolve("Deep Purple - 1974 - Burn")).resolve("01.mp3");
		createMp3Track(a, "Deep Purple", "Burn", "Burn", "Deep Purple", "1974", "1");
		Files.copy(a, b);
		AudioFile f = AudioFileIO.read(b.toFile());
		f.getTag().setField(FieldKey.COMMENT, "different tag, same audio");
		AudioFileIO.write(f);

		runImport(source, destination);

		Path album = destination.resolve("Deep Purple").resolve("1974 - Burn");
		assertThat(Files.list(album).map(p -> p.getFileName().toString()))
		                                                                  .containsExactlyInAnyOrder("01 - Burn.mp3", "01 - Burn (2).mp3");
		assertThat(entriesFor(a).get(0).action.name()).isNotEqualTo("DISCARDED_DUPLICATE");
		assertThat(entriesFor(b).get(0).action.name()).isNotEqualTo("DISCARDED_DUPLICATE");
		assertThat(ReviewItemEntity.<ReviewItemEntity>list("trackPhysicalPath", b.toString())).extracting(e -> e.category.name())
		                                                                                      .contains("IDENTICAL_DUPLICATE");
	}

	private void runImport(Path source, Path destination) throws IOException {
		command.source = source;
		command.destination = destination;
		command.call();
	}

	private static List<LedgerEntryEntity> entriesFor(Path source) {
		return LedgerEntryEntity.list("source", source.toString());
	}

	private static void createMp3Track(
	                                   Path destination, String artist, String title, String album, String albumArtist, String year, String track) throws Exception {
		createMp3Track(destination, artist, title, album, albumArtist, year, track, 8000);
	}

	private static void createMp3Track(
	                                   Path destination, String artist, String title, String album, String albumArtist, String year, String track, int sampleRate) throws Exception {
		generateSilentMp3(destination, sampleRate);
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

	private static void generateSilentMp3(Path destination, int sampleRate) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(
		        "ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=" + sampleRate + ":cl=mono", "-t", "0.2", "-codec:a", "libmp3lame", destination
		                                                                                                                                        .toString())
		                                                                                                                                                    .redirectErrorStream(true)
		                                                                                                                                                    .start();
		if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
			throw new IOException("ffmpeg did not generate the test file: " + destination);
		}
	}
}
