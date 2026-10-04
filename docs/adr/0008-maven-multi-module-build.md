# ADR-008: Build tool and module structure: Maven, multi-module

**Status**: accepted, 2026-07-16. Note of 2026-09-27: the `orpheus-web` module was removed (it was
empty; the review interface is to be redesigned).

## Context

With Quarkus chosen as the application framework (ADR-007) and the internal architecture of the
domain still to be fixed (see ADR-009), a concrete Java project structure was needed: build tool and
module granularity.

## Decision

Maven, with a multi-module structure.

Reasons: the user's explicit preference; consistency with the Quarkus ecosystem, which documents and
tests mostly on Maven; the multi-module split allows domain, persistence adapter, external service
adapters, CLI and web UI to be physically isolated in independent build units, making the boundaries
of the hexagonal architecture (ADR-009) checkable at the level of dependencies between modules. A
domain module that cannot even declare a dependency on Hibernate is a stronger constraint than a
package convention respected only by discipline.

## Consequences

Name and exact list of the modules (e.g. domain, persistence, MusicBrainz/AcoustID integration, CLI,
web UI) were left to the concrete scaffolding, once ADR-009 on layering was closed: this ADR fixes
only the build tool and the multi-module principle.
