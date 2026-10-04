package org.orpheus.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.orpheus.domain.model.LedgerAction;
import org.orpheus.domain.model.ReviewCategory;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.LedgerEntry;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.RawSignal;
import org.orpheus.domain.model.ReviewItem;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.ReviewStatus;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReviewQueuePort;
import org.orpheus.domain.port.LedgerPort;
import org.orpheus.persistence.entity.LedgerEntryEntity;
import org.orpheus.persistence.entity.ReviewItemEntity;
import org.orpheus.persistence.entity.RawSignalType;

/**
 * Runtime check (not only at build time, ADR-010) that the configured SQLite datasource, the community
 * dialect and the Flyway migrations really work together: it starts the real datasource, lets Flyway
 * apply V1, writes through the ports and reads back from the Panache entities.
 */
@QuarkusTest
class PersistenceRuntimeTest {

	@Inject
	LedgerPort ledger;

	@Inject
	ReviewQueuePort reviewQueue;

	@Inject
	EntityManager entityManager;

	@Test
	@Transactional
	void recordingALedgerEntryWritesAndReadsBackFromSqlite() {
		String marker = UUID.randomUUID().toString();
		Path source = Path.of("/source/%s.flac".formatted(marker));
		Path destination = Path.of("/destination/%s.flac".formatted(marker));
		Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

		LedgerEntry entry = new LedgerEntry(
		        source, destination, LedgerAction.IMPORTED_ENRICHED, "SQLite runtime check", 0.9, recordedAt);

		ledger.record(entry);

		// flush and clear force a genuine read from SQLite instead of returning the managed instance just
		// written by the persistence context (identity map): otherwise the test would pass even with a
		// broken dialect round trip (e.g. a truncated or corrupted Instant).
		entityManager.flush();
		entityManager.clear();

		LedgerEntryEntity read = LedgerEntryEntity.<LedgerEntryEntity>find("source", source.toString()).firstResult();

		assertThat(read).isNotNull();
		assertThat(read.destination).isEqualTo(destination.toString());
		assertThat(read.action).isEqualTo(LedgerAction.IMPORTED_ENRICHED);
		assertThat(read.reason).isEqualTo("SQLite runtime check");
		assertThat(read.confidence).isEqualTo(0.9);
		assertThat(read.recordedAt).isEqualTo(recordedAt);
	}

	@Test
	@Transactional
	void recordingAReviewItemWithSeveralSignalsWritesAndReadsBackTheChildren() {
		String marker = UUID.randomUUID().toString();
		Track track = track(marker);

		ReviewItem item = new ReviewItem(
		        track, ReviewCategory.UNCERTAIN_IDENTIFICATION, "two competing signals to reconcile", ReviewStatus.PENDING, List.of(new RawSignal.MatchDistance(0.12, 0.8), new RawSignal.FingerprintScore(0.77, 0.95)));

		reviewQueue.record(item);

		entityManager.flush();
		entityManager.clear();

		ReviewItemEntity read = ReviewItemEntity.<ReviewItemEntity>find("trackPhysicalPath", track.physicalPath()
		                                                                                          .toString())
		                                        .firstResult();

		assertThat(read).isNotNull();
		assertThat(read.category).isEqualTo(ReviewCategory.UNCERTAIN_IDENTIFICATION);
		assertThat(read.status).isEqualTo(ReviewStatus.PENDING);
		assertThat(read.trackArtist).isEqualTo("Artist " + marker);
		assertThat(read.signals).hasSize(2);
		assertThat(read.signals).extracting(s -> s.type)
		                        .containsExactlyInAnyOrder(RawSignalType.MATCH_DISTANCE, RawSignalType.FINGERPRINT_SCORE);
	}

	@Test
	@Transactional
	void recordingAReviewItemWithoutSignalsWritesNoChildRows() {
		String marker = UUID.randomUUID().toString();
		Track track = track(marker);

		ReviewItem item = new ReviewItem(
		        track, ReviewCategory.UNIDENTIFIED, "no reliable signal available", ReviewStatus.PENDING, List.of());

		reviewQueue.record(item);

		entityManager.flush();
		entityManager.clear();

		ReviewItemEntity read = ReviewItemEntity.<ReviewItemEntity>find("trackPhysicalPath", track.physicalPath()
		                                                                                          .toString())
		                                        .firstResult();

		assertThat(read).isNotNull();
		assertThat(read.category).isEqualTo(ReviewCategory.UNIDENTIFIED);
		assertThat(read.signals).isEmpty();
	}

	private static Track track(String marker) {
		TrackMetadata metadata = new TrackMetadata(
		        "Artist " + marker, "Title " + marker, "Album " + marker, null, 2001, 3, 1, ExternalIds.unknown());
		Provenance provenance = new Provenance(Path.of("/source/%s.mp3".formatted(marker)), ProvenanceState.INTACT);
		return new Track(Path.of("/destination/%s.mp3".formatted(marker)), metadata, provenance);
	}
}
