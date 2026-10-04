package org.orpheus.domain.port;

import java.util.Optional;
import org.orpheus.domain.identification.TextClassification;
import org.orpheus.domain.model.Track;

/**
 * Fallback text classification for Tier 3 (SPEC.md 4.4): when not even the fingerprint identifies a
 * track, a text heuristic or a language model can suggest artist, album and title from the file or
 * folder name. The suggestion always stays subject to human confirmation (SPEC.md section 9): it is
 * never applied automatically to the destination.
 *
 * <p>Implementation 3a (deterministic heuristic) exists now; 3b (LLM adapter) is a port left open for
 * the future (ADR-0017).
 */
public interface TextClassificationPort {

	Optional<TextClassification> classify(Track track);
}
