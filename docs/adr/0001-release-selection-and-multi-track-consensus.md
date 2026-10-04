# ADR-001: Release selection and multi-track consensus in Tier 2

**Status**: accepted, 2026-07-15. Amended by ADR-0018 (2026-09-26): compilations and releases of an
artist other than the recording's lose against the artist's releases, see point B.

## Context

Tier 2 chooses a release among those a fingerprint-identified recording appears on. A first test on
a real case (a CD never tagged, then recognized as a well-known studio album with two tracks that
had ended up on a related single/EP) showed two problems: the "oldest release" choice can fall on a
lead single instead of the album, and several tracks of the same CD, identified independently of
each other, risk ending up in different folders even when they clearly belong to the same physical
disc.

## Decision

A release of type Album always beats a release of type EP or Single, whatever the date: a single that
anticipates the album is not a distinct recording, just an earlier release of the same master. Among
releases of the same type the oldest by precise date wins, fixing a bug found along the way: a date
known only by year must not beat a more precise date in the same year.

For multi-track consensus: it applies only to groups with placeholder tags, never to just any
folder; with a 65% convergence threshold the whole group is forced onto the same exact release, with
the safeguard that the forced minority tracks must still have a match on the winning release; below
the threshold the individual behaviour applies, with no intermediate levels in this first version.

## Consequences

There is no need to query the release type (Album/EP/Single/Live/Compilation) with a complex
priority table: the Album-over-EP/Single priority alone, verified to be available in the same call
already used to fetch releases, solves almost all real cases of this kind.

An imprecision is knowingly accepted: a release with truly exclusive content (a remix present only on
the single) can stay separate from the album even when the rest of the disc is forced together.
That is preferable to treating it as a duplicate and losing it.
