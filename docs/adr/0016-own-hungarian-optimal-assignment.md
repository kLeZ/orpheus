# ADR-016: Optimal file-to-track assignment: own implementation (Hungarian algorithm)

**Status**: accepted, 2026-07-16.

## Context

ADR-006 had already fixed the need for this component (a typed reimplementation of beets's
`match.py`, which uses the Jonker-Volgenant algorithm on a cost matrix). At the scale of an album,
dozens of files and not thousands, the performance difference between Jonker-Volgenant and the
classic Hungarian algorithm (Kuhn-Munkres) is irrelevant: both guarantee the same optimal result, so
ADR-006 constrains the optimality guarantee, not the specific algorithm. Unlike the Levenshtein
distance (ADR-015), a bug in this component silently matches the wrong file to the wrong track with no
visible error: it is a more correctness-critical component, with more edge cases than a simple DP
(rectangular matrices when the number of files differs from the number of candidate tracks, infinite or
inadmissible costs for pairs to exclude up front).

## Decision

Own implementation of the Hungarian algorithm (Kuhn-Munkres), no external library: the same direction
as ADR-015, zero dependencies in the domain module (ADR-009), explicit control (ADR-007). The decision
was taken knowingly despite the higher correctness risk compared with a mature library such as
JGraphT, accepted on condition of a proportionate testing effort (see Consequences).

## Consequences

It needs a more extensive unit test suite than ADR-015, with explicit edge cases: a simple square
matrix; a rectangular matrix (more files than candidate tracks and vice versa); infinite or
inadmissible costs for file-track pairs to exclude up front; a matrix with several optimal solutions,
to check that any valid choice is accepted and not just a specific one; a 1×1 matrix and degenerate
cases (zero files or zero tracks). Without an external regression oracle (the comparison with the
Python pipeline was explicitly excluded from the scope of this design work), correctness must be
established up front by the test cases, not verified afterwards by comparison with a known behaviour.
