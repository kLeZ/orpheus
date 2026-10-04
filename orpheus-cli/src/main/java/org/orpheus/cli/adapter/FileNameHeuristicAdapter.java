package org.orpheus.cli.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.orpheus.domain.identification.TextClassification;
import org.orpheus.domain.identification.FileNameHeuristic;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.TextClassificationPort;

/**
 * CDI adapter for the Tier 3a text heuristic (ADR-0017). It delegates to {@link FileNameHeuristic},
 * which is pure domain logic without infrastructure dependencies.
 */
@ApplicationScoped
public class FileNameHeuristicAdapter implements TextClassificationPort {

	@Override
	public Optional<TextClassification> classify(Track track) {
		return FileNameHeuristic.classify(track);
	}
}
