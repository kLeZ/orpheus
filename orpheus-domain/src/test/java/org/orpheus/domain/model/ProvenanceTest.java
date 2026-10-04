package org.orpheus.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProvenanceTest {

	@Test
	void buildsWithSourceAndState() {
		var provenance = new Provenance(Path.of("/source/track.flac"), ProvenanceState.INTACT);

		assertThat(provenance.source()).isEqualTo(Path.of("/source/track.flac"));
		assertThat(provenance.state()).isEqualTo(ProvenanceState.INTACT);
	}

	@Test
	void rejectsANullSource() {
		assertThatThrownBy(() -> new Provenance(null, ProvenanceState.INTACT))
		                                                                      .isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsANullState() {
		assertThatThrownBy(() -> new Provenance(Path.of("/x"), null))
		                                                             .isInstanceOf(NullPointerException.class);
	}
}
