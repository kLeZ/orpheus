package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

	@Test
	void buildsAnEnrichedImportEntry() {
		var entry = new LedgerEntry(
		        Path.of("/source/t1.mp3"), Path.of("/dest/Artist/1998 - Album/01 - Title.mp3"), LedgerAction.IMPORTED_ENRICHED, "identified through MusicBrainz, tags corrected", 0.92, Instant.parse("2026-07-23T10:00:00Z"));

		assertThat(entry.action()).isEqualTo(LedgerAction.IMPORTED_ENRICHED);
		assertThat(entry.confidence()).isEqualTo(0.92);
	}

	@Test
	void aDeterministicActionCanRecordFullConfidence() {
		var entry = new LedgerEntry(
		        Path.of("/source/t1.mp3"), Path.of("/dest/Unknown/t1.mp3"), LedgerAction.IMPORTED_AS_IS, "no identification available, copied as is", 1.0, Instant.now());

		assertThat(entry.confidence()).isEqualTo(1.0);
	}

	@Test
	void rejectsZeroConfidence() {
		assertThatThrownBy(
		        () -> new LedgerEntry(
		                Path.of("/a"), Path.of("/b"), LedgerAction.RECONSTRUCTED, "reason", 0.0, Instant.now()))
		                                                                                                        .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsConfidenceAboveOne() {
		assertThatThrownBy(
		        () -> new LedgerEntry(
		                Path.of("/a"), Path.of("/b"), LedgerAction.RECONSTRUCTED, "reason", 1.1, Instant.now()))
		                                                                                                        .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankReason() {
		assertThatThrownBy(
		        () -> new LedgerEntry(
		                Path.of("/a"), Path.of("/b"), LedgerAction.RECONCILED, "  ", 0.5, Instant.now()))
		                                                                                                 .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNullRequiredFields() {
		assertThatThrownBy(
		        () -> new LedgerEntry(
		                null, Path.of("/b"), LedgerAction.RECONCILED, "reason", 0.5, Instant.now()))
		                                                                                            .isInstanceOf(NullPointerException.class);
	}
}
