package org.orpheus.domain.ingest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of the rules of ADR-0021 for a file: the values to write, the original values to keep in the
 * file ({@code ORPHEUS_ORIGINALE_<CAMPO>}, rule T24) and the corrections to record in the database
 * (rule T26). A field absent from {@code values} must not be touched.
 */
public record TagsToWrite(Map<TagField, List<String>> values,
                          Map<TagField, List<String>> originals,
                          List<Correction> corrections) {

	public record Correction(TagField field, List<String> before, List<String> after) {
	}

	public TagsToWrite {
		Objects.requireNonNull(values, "values");
		Objects.requireNonNull(originals, "originals");
		Objects.requireNonNull(corrections, "corrections");
		values = Map.copyOf(values);
		originals = Map.copyOf(originals);
		corrections = List.copyOf(corrections);
	}
}
