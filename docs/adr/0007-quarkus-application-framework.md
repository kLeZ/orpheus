# ADR-007: Application framework: Quarkus

**Status**: accepted, 2026-07-16.

## Context

After ADR-006 (Java/JVM stack) the choice of application framework was still open, with a cascading
impact on dependency injection, configuration, logging, the web layer, and partly also the CLI and
data access. A weighted questionnaire was run on four candidates (Spring Boot, Quarkus, Micronaut, no
framework with targeted libraries) along four criteria weighted on the user's stated preferences:
fast start of CLI commands (low weight, declared not a priority), support for a GraalVM native
executable (medium weight, "useful but not a priority"), explicit control versus framework
conventions and magic (high weight, consistent with the preference already expressed in ADR-006 for
pure OOP and strong typing), and the ability to serve both the CLI and the `/review` web UI from a
single artifact (medium weight).

The weighted score had given "no framework" as the winner (41 points) ahead of Quarkus and Micronaut
(32 points each) and Spring Boot (18 points). In practice, however, "no framework" means writing from
scratch, or choosing and wiring by hand library by library, all the application infrastructure a
framework provides out of the box: dependency injection, configuration, logging, web layer. With
beets abandoned (ADR-006) the project already carries a non-trivial rewrite of the domain logic
(matching engine, fingerprint, review queue); adding the application infrastructure by hand too would
have multiplied the minor ADRs needed (DI, logging, configuration, besides the web layer, CLI and
persistence already planned) and the design work, with no proportional benefit for a project
maintained by one person.

## Decision

Application framework: **Quarkus**, not Spring Boot and not "no framework".

Compared with Spring Boot, Quarkus is the choice that best respects the criteria already fixed in the
questionnaire:

- compile-time dependency injection (Arc), not runtime scanning and proxies as in Spring: less hidden
  magic, consistent with the explicit-control criterion that weighed most in the original
  questionnaire;
- GraalVM native image support as a design goal from the project's origin, not an afterthought;
- the Picocli extension for the CLI and RESTEasy Reactive for the web UI can live in the same
  artifact, consistent with the "single process for CLI and web UI" criterion;
- data access not tied to an ORM: Hibernate/Panache is available but not mandatory, direct JDBC/jOOQ
  remains possible if preferable for the desired level of control.

Compared with "no framework", Quarkus provides configuration (MicroProfile Config), logging (JBoss
Logging on SLF4J) and lifecycle/DI management out of the box, avoiding separate ADRs to open and
maintain for each of these infrastructure pieces: a choice driven by the practical need to contain
the design scope, not by a rethinking of the technical criteria of the original questionnaire.

Trade-off knowingly accepted: compared with Spring Boot, Quarkus has a smaller community and fewer
ready answers online; for non-obvious problems it may be necessary to read the source of the
extensions instead of finding a ready answer.

## Consequences

- The ADRs originally planned for logging and configuration as separate items are absorbed by Quarkus
  conventions (documented here, not in separate ADRs), the same logic ADR-006 had already anticipated
  for the web layer.
- The ADR planned for the dependency injection pattern (manual wiring versus a light library) is no
  longer needed: Quarkus provides Arc.
- The ADRs still open at the time (build tool and module structure, internal layering, storage engine,
  data access level, schema migrations, external service clients, weighted multi-field distance,
  optimal file-to-track assignment, concurrency model, CLI, `/review` frontend, packaging) remained to
  be decided, but with the set of concrete options narrowed by this framework choice: for data access,
  for example, the realistic options become Panache/Hibernate ORM with Quarkus or JDBC/jOOQ inside the
  same runtime, no longer an open spectrum over any Java library.
- If native image mode is adopted in the future (packaging ADR, still open), the compatibility of the
  libraries already fixed by ADR-006 (jaudiotagger, TarsosDSP) must be checked in practice, not just
  assumed from the documentation: they will probably need explicit reflection configuration.
