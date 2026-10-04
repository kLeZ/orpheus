# ADR-012: Command-line interface: Picocli

**Status**: accepted, 2026-07-16.

## Context

Orpheus needs a command-line interface for the pipeline commands (import, quality-scan,
reconstruct-orphans and the like, already present in concept in the Python version). Quarkus
(ADR-007) offers a dedicated CLI extension, integrated with the rest of the framework.

## Decision

Picocli, through the Quarkus extension of the same name: typed parsing of arguments and options,
automatically generated help, native integration with Arc (the Quarkus DI container) to inject the
domain use cases directly into the commands.

## Consequences

None significant beyond what ADR-007 already implies. The exact structure of the commands (a root
command with subcommands versus independent commands) remains an implementation detail, not an
architectural one.
