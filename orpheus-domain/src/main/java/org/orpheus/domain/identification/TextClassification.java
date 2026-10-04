package org.orpheus.domain.identification;

import java.util.Optional;

/**
 * Suggestion produced by a Tier 3 text classification (SPEC.md 4.4, ADR-0017): artist, album and
 * title inferred from the file or folder name, with a confidence in (0, 1] (ADR-004 convention).
 */
public record TextClassification(
                                 Optional<String> artist,
                                 Optional<String> album,
                                 Optional<String> title,
                                 double confidence) {
}
