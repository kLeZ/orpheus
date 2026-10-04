package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Optional;
import org.orpheus.domain.port.ComposersPort;
import org.orpheus.domain.port.TracklistPort;

/** Fake of tracklist and authors without network: no data. */
@Alternative
@Priority(1)
@ApplicationScoped
public class TracklistAndComposersFake implements TracklistPort, ComposersPort {

	@Override
	public Optional<List<String>> titles(String releaseId) {
		return Optional.of(List.of());
	}

	@Override
	public Optional<List<String>> composers(String recordingId) {
		return Optional.of(List.of());
	}
}
