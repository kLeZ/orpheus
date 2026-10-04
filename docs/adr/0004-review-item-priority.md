# ADR-004: Priority of review items in the review queue

**Status**: accepted, 2026-07-15.

## Context

Different categories of review item carry signals of a different nature (the distance of a match,
the deviation ratio of a duration, a spectral anomaly score, a fingerprint score, or no reliable
signal at all for unidentified tracks), but the queue sorts them all together. A way was needed to
keep them distinct and at the same time comparable, without a hand-written exception for the cases
with no signal at all.

## Decision

Every review item keeps its own raw signal, typed by category, always queryable on its own.

On top of it, a derived priority value, continuous and not in bands, is obtained by expressing each
signal as a confidence (0 excluded, 1 included: how sure we are that there is nothing to correct)
and summing the weighted negative logarithms of these confidences. It is the same principle as
Shannon's information content and inverse-variance weighting in statistics:

```text
priority = - Σ (weight_i · log(confidence_i))
```

A track left with no signal at all has zero confidence, and the formula makes its priority diverge
to infinity by construction, not by a hand-coded special case.

## Consequences

The weights stay hand-picked, consistently with the decision already taken for the multi-track
consensus threshold (ADR-001): a trained statistical model makes sense only if in the future more
correlated parameters emerge to be tuned together, not before.

A practical implementation of the formula will still need a minimum confidence value other than
zero, so as not to have to represent an infinite or undefined value: it is an implementation detail,
not a change of the model.
