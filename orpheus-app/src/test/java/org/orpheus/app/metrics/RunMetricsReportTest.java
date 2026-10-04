package org.orpheus.app.metrics;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orpheus.app.metrics.LedgerMetrics.Entry;
import org.orpheus.domain.port.UnreadableTrackException;
import org.orpheus.tagging.reading.JaudiotaggerTagReader;

/**
 * Report of the metrics on a real ledger (corrective plan, docs/ROADMAP.md). Skipped in normal builds;
 * run it with:
 *
 * <pre>
 * mvn -pl orpheus-app test -Dtest=RunMetricsReportTest -Dsurefire.failIfNoSpecifiedTests=false \
 * -Dorpheus.metrics.ledger=.../orpheus.db -Dorpheus.metrics.source=~/Musica \
 * -Dorpheus.metrics.destination=...
 * </pre>
 *
 * It prints the report and writes it to {@code target/run-metrics.txt}.
 */
class RunMetricsReportTest {

	@Test
	void report() throws SQLException, IOException {
		String ledger = System.getProperty("orpheus.metrics.ledger");
		assumeTrue(ledger != null, "orpheus.metrics.ledger not set: report skipped");
		Path source = Path.of(System.getProperty("orpheus.metrics.source"));
		Path destination = Path.of(System.getProperty("orpheus.metrics.destination"));

		List<Entry> entries = new ArrayList<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + ledger); ResultSet rs = c.createStatement()
		                                                                                          .executeQuery("select source, destination, action, reason from ledger_entry")) {
			while (rs.next()) {
				entries.add(new Entry(Path.of(rs.getString(1)), Path.of(rs.getString(2)), rs.getString(3), rs.getString(4)));
			}
		}

		JaudiotaggerTagReader reader = new JaudiotaggerTagReader();
		Map<Path, @Nullable String> artistCache = new HashMap<>();
		LedgerMetrics.Result r = LedgerMetrics.compute(entries, source, destination, p -> artistCache.computeIfAbsent(p, f -> {
			try {
				return reader.read(f).metadata().artist();
			} catch (UnreadableTrackException e) {
				return null;
			}
		}));

		String report = """
		        Ledger: %s
		        Entries: %d
		        Fake albums (title = artist or artist folder): %d
		        Files with an (N) suffix: %d
		        "Unknown Track" files: %d
		        Unidentified with a release id: %d
		        Year conflicting with the name of the source folder: %d
		        Tracks in albums with different artists (excluding Various Artists): %d
		        Tier 2 releases by an unrelated artist (excluding declared contrasts): %d
		        """.formatted(ledger, r.entries(), r.fakeAlbums(), r.numericSuffixes(), r.unknownTracks(), r.unidentifiedWithRelease(), r.conflictingYears(), r.tracksInAlbumsWithDifferentArtists(), r.releasesByUnrelatedArtists());
		StringBuilder withExamples = new StringBuilder(report);
		r.examples().forEach((metric, lines) -> {
			withExamples.append("\nExamples - ").append(metric).append(":\n");
			lines.forEach(line -> withExamples.append("  ").append(line).append('\n'));
		});
		report = withExamples.toString();
		System.out.println(report);
		Files.writeString(Path.of("target", "run-metrics.txt"), report);
	}
}
