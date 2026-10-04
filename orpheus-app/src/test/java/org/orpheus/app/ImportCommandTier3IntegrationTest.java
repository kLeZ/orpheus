package org.orpheus.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
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
import org.orpheus.app.identification.ReleaseLookupFake;
import org.orpheus.app.identification.FingerprintLookupFake;
import org.orpheus.cli.ImportCommand;
import org.orpheus.persistence.entity.LedgerEntryEntity;
import org.orpheus.persistence.entity.ReviewItemEntity;
import org.orpheus.persistence.entity.RawSignalEntity;
import org.orpheus.persistence.entity.RawSignalType;

/**
 * Checks the Tier 3a fallback (text heuristic on the file name, ADR-0017) inside {@code ImportCommand}:
 * when Tier 0/1 does not group and Tier 2 finds no match (the fakes return nothing), the file name is
 * analyzed to produce a suggestion in the review queue. It uses the Tier 2 fakes for determinism
 * without network.
 */
@QuarkusTest
@TestProfile(FakeTier2AdaptersProfile.class)
class ImportCommandTier3IntegrationTest {

	@Inject
	@TopCommand
	ImportCommand command;

	@Inject
	FingerprintLookupFake fingerprints;

	@Inject
	ReleaseLookupFake releases;

	@BeforeEach
	void clearFakes() {
		fingerprints.clear();
		releases.clear();
	}

	@Test
	void anIsolatedTrackWithoutTagsWithARecognizableFileNameProducesATier3Suggestion(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path isolated = source.resolve("Pink Floyd - Comfortably Numb.mp3");
		// No tag written: Tier 0/1 cannot group, Tier 2 has no fingerprints set -> residue. The file name
		// triggers the Tier 3a heuristic.
		createMp3Track(isolated, null, null, null, null, null, null);

		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(isolated);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action.name()).isEqualTo("IMPORTED_AS_IS");

		List<ReviewItemEntity> items = itemsFor(isolated);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).category.name()).isEqualTo("UNCERTAIN_IDENTIFICATION");
		assertThat(items.get(0).reason).contains("Tier 3");

		List<RawSignalEntity> signals = RawSignalEntity.list("reviewItem", items.get(0));
		assertThat(signals).hasSize(1);
		assertThat(signals.get(0).type).isEqualTo(RawSignalType.TEXT_CLASSIFICATION_CONFIDENCE);
		assertThat(signals.get(0).confidence).isEqualTo(0.8);
	}

	@Test
	void anIsolatedTrackWithoutTagsWithAnUnrecognizableFileNameStaysUnidentified(@TempDir Path source, @TempDir Path destination) throws Exception {
		Path isolated = source.resolve("unknown_song.mp3");
		createMp3Track(isolated, null, null, null, null, null, null);

		runImport(source, destination);

		List<LedgerEntryEntity> entries = entriesFor(isolated);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action.name()).isEqualTo("IMPORTED_AS_IS");

		List<ReviewItemEntity> items = itemsFor(isolated);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).category.name()).isEqualTo("UNIDENTIFIED");

		List<RawSignalEntity> signals = RawSignalEntity.list("reviewItem", items.get(0));
		assertThat(signals).isEmpty();
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
