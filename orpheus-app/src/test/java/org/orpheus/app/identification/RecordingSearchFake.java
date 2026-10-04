package org.orpheus.app.identification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Optional;
import org.orpheus.domain.port.RecordingSearchPort;

/** Fake of {@link RecordingSearchPort} without network: no recording found. */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingSearchFake implements RecordingSearchPort {

	@Override
	public Optional<List<FoundRecording>> search(String artist, String title) {
		return Optional.of(List.of());
	}
}
