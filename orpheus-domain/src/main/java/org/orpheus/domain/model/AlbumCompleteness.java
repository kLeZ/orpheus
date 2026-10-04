package org.orpheus.domain.model;

/**
 * How much is known about an album compared with its real tracklist (SPEC.md section 3). An isolated
 * track having no album at all is not a value of this enum: it is simply the absence of an
 * {@link Album} for that track.
 *
 * <p>{@link #UNVERIFIED} covers the Tier 0/1 grouping (SPEC.md section 4.4): tracks reconciled by
 * folder or by consistent tags, with no external source consulted. In that case the album is neither
 * {@link #COMPLETE} nor {@link #PARTIAL} in the sense of section 3: both assume a check that has not
 * happened yet, and we do not know whether tracks are missing. Forcing one of the two would be an
 * unfounded assertion, against the principle of not treating the physiological chaos of the source as
 * an edge case to be squeezed in (DESIGN.md).
 */
public enum AlbumCompleteness {
	COMPLETE,
	PARTIAL,
	UNVERIFIED
}
