/**
 * Declarative client for the MusicBrainz REST API and the adapters implementing the lookup ports of the
 * domain. The rate limiter (1 request per second, ADR-014) is a single component shared per external
 * service, not duplicated per call.
 */
package org.orpheus.musicbrainz;
