# ADR-0017: Tier 3 fallback and the 0→1→2→3 cascade as a domain component

**Status**: accepted, 2026-09-16.

## Context

The orchestration between identification tiers (SPEC.md 4.4) lived entirely in `ImportCommand`
(orpheus-cli), mixing domain logic (which tier to apply to which track) with infrastructure logic
(file copy, ledger writes). That made the orchestration hard to test in isolation and dependent on the
Quarkus/Picocli runtime.

Tier 3 (SPEC.md 4.4: the "residual fallback" for tracks not identifiable even by fingerprint) did not
exist yet. The specification describes it as a last-resort mechanism, "probably assisted by a language
model", but with two firm constraints: (1) algorithmic first (DESIGN.md), so a deterministic text
heuristic must come before any LLM; (2) automatic resolution of Tier 3 cases without confirmation is
explicitly out of scope (SPEC.md section 9), so the Tier 3 result is a suggestion for review, never an
automatic application.

## Decision

- **Cascade moved into the domain**: `IdentificationPipeline` in `orpheus-domain` takes tracks and
  ports and produces structured outcomes per track (`TrackOutcome`), in the sequence 0→1→2→3.
  `ImportCommand` only scans, reads the tags and consumes the outcomes.
- **Tier 3 in two stages**: 3a is a deterministic text heuristic on the file and folder name
  (`FileNameHeuristic`), implemented now; 3b is a `TextClassificationPort` for a future LLM adapter
  (Anthropic, already provided for in `.env.example`), postponed until a genuine real case justifies
  it.
- **Never applied automatically**: the Tier 3 suggestion goes into the review queue as
  `UNCERTAIN_IDENTIFICATION` with its confidence as the raw signal. The track is copied to the
  existing path (Tier 0/1 or isolated), not enriched with the proposed metadata.
- **Always on**: heuristic 3a has no API keys or configuration and is always active. Adapter 3b, when
  it comes, will follow the AcoustID pattern (missing key means disabled).

## Consequences

The domain pipeline can be tested with fake ports without starting Quarkus. Tier 3a increases coverage
of isolated tracks with a readable file name, without requiring external API keys. The known limit of
Tier 3a is that it works only on recognizable patterns in the file name: it does not solve tracks with
generic names ("Track 01") or content without textual context, which stay `UNIDENTIFIED` until 3b
arrives.
