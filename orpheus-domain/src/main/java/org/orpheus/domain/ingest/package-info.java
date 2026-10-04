/**
 * Destination structure of the import (SPEC.md section 4.3, ADR-003): pure computation of the path a
 * track is copied to, distinct from the {@code identification} package, which instead resolves
 * *which* album or release a track belongs to (SPEC.md sections 4.4-4.5). Here the identification is
 * assumed to have happened already (or to be explicitly absent); the only problem is where the file
 * lands.
 */
package org.orpheus.domain.ingest;
