package org.orpheus.domain.identification;

/**
 * MusicBrainz release type relevant to the Tier 2 choice (SPEC.md section 4.4, ADR-001). OTHER covers
 * the types the record industry does not treat as a standalone release for this choice (Live,
 * Compilation, Broadcast, ...): the MusicBrainz adapter maps them here instead of having to be
 * updated every time a type not yet foreseen shows up. For rule 1 it behaves like EP/SINGLE: only
 * ALBUM counts as "the canonical release" that beats them all.
 */
public enum ReleaseType {
	ALBUM,
	EP,
	SINGLE,
	OTHER
}
