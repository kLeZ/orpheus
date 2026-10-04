/**
 * Ports (interfaces) to everything the domain needs from outside: persistence, MusicBrainz lookup,
 * AcoustID fingerprints, audio tag I/O, review queue notification (ADR-009). The adapters that
 * implement them live in other modules and depend on this one, never the other way round.
 */
package org.orpheus.domain.port;
