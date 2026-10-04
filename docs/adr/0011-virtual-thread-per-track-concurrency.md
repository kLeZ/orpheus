# ADR-011: Concurrency model: one virtual thread per track

**Status**: accepted, 2026-07-16.

## Context

The pipeline processes a potentially large number of tracks (whole music libraries), with steps that
involve blocking I/O: rate-limited HTTP calls to MusicBrainz and AcoustID, invocation of the external
`fpcalc` process, reading and writing tags on files. The JDK train chosen in ADR-006 (26/27) offers
virtual threads (Project Loom), mature exactly for this pattern of highly concurrent blocking I/O.

## Decision

One virtual thread per processed track, with progress recorded in the database (SQLite, ADR-010) as
each pipeline phase for that track completes. Not a reactive model (no Mutiny or reactive streams): a
choice consistent with the preference for explicit, readable code already expressed in ADR-007, since
virtual threads allow "normal" blocking code to be written without paying its scalability cost.

## Consequences

Rate limiting towards MusicBrainz (1 request per second under the service's guidelines) and AcoustID
must be implemented as a limit shared among the virtual threads (a semaphore or an explicit
client-side limiter), not delegated implicitly to the concurrency model; the concrete choice belongs
to the ADR on external service clients, still open at the time. Concurrent state writes to SQLite from
several virtual threads must be checked against the SQLite write concurrency limits already noted in
ADR-010.
