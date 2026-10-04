# ADR-015: Weighted multi-field distance: own implementation of normalized Levenshtein

**Status**: accepted, 2026-07-16.

## Context

ADR-006 already fixed the method for Tier 0/1: a weighted multi-field distance based on normalized
Levenshtein (a typed reimplementation of beets's `distance.py`/`match.py`), not an open choice. What
remained to decide was how to implement the elementary block, the distance between two strings, on
which the weighted multi-field combination is built; that combination is domain logic under ADR-009.

## Decision

Own implementation of the normalized Levenshtein algorithm, no external library.

Reasons: it is a well-known dynamic programming algorithm, a few dozen lines of code, already judged
"feasible with acceptable effort" in ADR-006; it keeps the domain module free of external dependencies
even for pure algorithmic logic, consistent with the hexagonal architecture of ADR-009; and it fits
the preference for explicit control that was already decisive in ADR-007.

## Consequences

The weighted multi-field combination (which fields, which weights, the behaviour on placeholder or
empty tags that ADR-006 already notes saturates the distance at the maximum) remains domain logic to
design separately, not decided by this ADR. Text normalization before comparison (case folding,
diacritics and accents, punctuation) is to be decided at implementation time: it is a requirement
independent of the build-versus-library choice, needed anyway for reliable comparisons of non-English
artists and albums, always present in the music domain. It must be covered by unit tests on the edge
cases a ready library would have guaranteed out of the box (empty strings, Unicode, very different
lengths).
