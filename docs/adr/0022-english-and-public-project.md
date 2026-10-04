# ADR-0022: English for code and documentation, public project

**Status**: accepted, 2026-09-28. Migration completed and project published on 2026-10-04: plan and
record in [`docs/ENGLISH-MIGRATION.md`](../ENGLISH-MIGRATION.md).

## Context

Since 2026-07-24 Orpheus's code (class, method and variable names, comments) had been in Italian, for
consistency with the ubiquitous language of the documentation (SPEC.md, DESIGN.md, ADRs). It was an
explicit exception to the user's general instruction to write code in English, noted in `CLAUDE.md`
and to be reconsidered "only if the project became public". On 2026-09-28 the user decided to make
the project public.

## Decision

1. The whole project moves to English: code, comments, tests, CLI messages, review item texts,
   documentation (README, DESIGN, SPEC, ADRs, ROADMAP, TODO, agent instructions).
2. The ubiquitous language stays a single one, now English: first the glossary of domain terms is
   fixed, then documents and code are translated with the same terms.
3. The exception in `CLAUDE.md` is removed when the migration is complete. Until then new code stays
   in Italian, so as not to have a project halfway between the two languages.
4. The git history is not rewritten for the language: past commit messages stay in Italian.

## Alternatives considered

- **Code only in English, documentation in Italian**: discarded. The project becomes public, and
  English code with terms defined in Italian documents breaks the ubiquitous language.
- **Stay in Italian even when public**: discarded by the user.

## Consequences

- A broad migration, in phases, each with a green build and tests (plan in `docs/ENGLISH-MIGRATION.md`).
- The database schema (tables `movimento`, `segnalazione`, `segnale_grezzo`, enum values stored as
  text) changes. Since no real database existed yet (only test runs, deletable by the user's word of
  2026-10-04), the initial schema itself was rewritten in English instead of adding a migration.
- User-visible names change: the `--simula` option (now `--dry-run`), the `ORPHEUS_ORIGINALE_<CAMPO>`
  fields written into files (now `ORPHEUS_ORIGINAL_<FIELD>`), cache categories, review item texts.
  The folder placeholders were already moved to English and made configurable on 2026-09-28
  (`orpheus.placeholders.*`).
- License: AGPL-3.0-or-later (the user's choice of 2026-09-28). Before publication the history is
  cleaned up, so that no commit without a license stays reachable.
