# ADR-005: Classical music postponed, not excluded

**Status**: postponed, 2026-07-15.

## Context

While discussing compilations (ADR-003) the much more complex case of classical music came up, where
composer, performer, album artist and track artist are often all different within the same track.

## Decision

Do not design for this case now. It is not permanently excluded from Orpheus's scope: there is simply
no real material from this domain in the library yet to design on and verify in practice, and it is a
current collection choice of the user, not a scope decision of the project.

## Consequences

When (if) classical music arrives in the library, the functional specification must be extended with
a dedicated section before trusting the behaviour of Tier 0-3 on that domain.
