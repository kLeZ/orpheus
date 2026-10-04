# ADR-009: Internal layering: hexagonal architecture (ports and adapters)

**Status**: accepted, 2026-07-16.

## Context

Orpheus's domain logic (cataloguing pipeline, Tier 0/1/2/3 identification engine, multi-track
consensus, review queue priority formula) must stay decoupled from the technical details fixed in the
other ADRs (SQLite, MusicBrainz/AcoustID over HTTP, Quarkus/Picocli/REST). It has already happened,
with beets abandoned (ADR-006), that a stack change forced a rewrite of the infrastructure while the
domain logic was kept: the internal structure of the new system must make this the normal case, not a
risk to manage case by case.

## Decision

Hexagonal architecture (ports and adapters). The domain (Track, Album, LedgerEntry, ReviewItem, the
tiered identification engine, the priority formula of ADR-004) defines ports, that is interfaces, for
everything it needs from the outside: persistence, MusicBrainz search, AcoustID fingerprint,
notification to the review queue. It depends on no infrastructure library. The adapters
(Hibernate/Panache for persistence, HTTP clients for MusicBrainz/AcoustID, Picocli for the CLI, the
web UI) implement those ports and depend on the domain, never the other way round.

## Consequences

Every later ADR that fixes an infrastructure library (external service clients, ORM and so on) must be
written with the adapter that will use it in mind, not the domain. The domain stays free of
dependencies on Quarkus, Hibernate or any HTTP client for all the logic described in SPEC.md; the
persistence entities (ADR-010) stay mapped by classes separate from the domain model exposed by the
ports, not the same object.
