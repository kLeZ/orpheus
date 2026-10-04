# ADR-010: Persistence: SQLite, Flyway, Hibernate ORM with Panache (repository pattern)

**Status**: accepted, 2026-07-16.

## Context

SPEC.md requires the system to keep the full history of ledger entries for every organized file
(section 3: "it must always be possible to reconstruct the full history of an organized file, not only
its final state") and that running an operation again does not duplicate entries or corrupt the state
(section 8). These two requirements imply a database that persists across runs, not a temporary
database per run: the doubt whether the database could be ephemeral, raised in discussion, is already
settled by these requirements; it was not an open ambiguity to decide here.

Since Orpheus is a single-user desktop application, a separate database server (Postgres, MySQL)
would impose on the user an infrastructure out of proportion to the use case: it is not reasonable to
ask someone who only wants to organize their music to set up a database server.

## Decision

- **Engine**: SQLite, the only realistic embedded database for a single-user desktop, with no
  installation or server process required of the user.
- **Migrations**: Flyway from the start, not postponed. A direct consequence of the persistent nature
  of the database above: future schema updates must run on the data already there without losing it.
- **Data access**: Hibernate ORM with Panache, in the **repository** pattern, not active record. The
  persistence entities stay mapped by classes separate from the domain model exposed by the ports
  (consistent with ADR-009: a Panache entity extending `PanacheEntity` would bring persistence into
  the domain object itself). Queries go through Panache's typed methods (`PanacheRepository`, finders
  and query methods) instead of free HQL/JPQL strings scattered through the code, to avoid the
  "stringly typed programming" meant to be ruled out.

## Consequences

Hibernate's compatibility with SQLite must be verified in practice on the real cases, not assumed from
the documentation, the same standard of rigour already applied in ADR-002 and ADR-006. SQLite has known
limits that may need specific care in the Flyway migrations or in the entity mapping: no generic
`ALTER COLUMN`, limited write concurrency (relevant for the virtual-thread-per-track model of ADR-011,
which writes state to the database from several threads), and a Hibernate dialect for SQLite
maintained by the community, not the official one of the Hibernate project.
