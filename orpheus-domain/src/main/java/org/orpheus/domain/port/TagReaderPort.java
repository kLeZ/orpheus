package org.orpheus.domain.port;

import java.nio.file.Path;
import org.orpheus.domain.model.Track;

/**
 * Reads the current metadata of an audio file (SPEC.md section 2, ADR-002: six formats on equal
 * footing). The adapter (ADR-014/jaudiotagger, module {@code orpheus-tagging}) implements this port;
 * the domain knows nothing about the library used to read the tags.
 *
 * <p>Absent, blank or placeholder tags are not an error (DESIGN.md: they are the base case, not the
 * exception): they produce a {@link Track} with the optional fields of {@code TrackMetadata} set to
 * {@code null}. {@link UnreadableTrackException} is reserved for the distinct case where the file
 * itself cannot be read (corrupted, a format outside the six supported): that one must be reported
 * instead of being silently ignored.
 */
public interface TagReaderPort {

	Track read(Path file) throws UnreadableTrackException;
}
