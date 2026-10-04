# ADR-003: Compilations, Various Artists and general destination pattern

**Status**: accepted, 2026-07-15.

## Context

The data model implicitly assumed a single artist per album. An explicit convention was needed for
compilations, where the artist changes track by track, and in doing so the general destination
pattern was also rethought.

## Decision

The general pattern becomes artist / year - album / disc if any / number - title (an intentional
choice, not just a format correction).

For compilations, the album artist is the conventional value "Various Artists", treated like any
other artist in the tree and not as a separate category; only in this case does the file name also
include the track artist: number - track artist - title.

Detection uses external identification when available, otherwise a local rule: at least two
different, non-empty track artists on the same album tag in the same grouping.

## Consequences

Classical music, where composer, performer, album artist and track artist are often all different,
stays explicitly postponed and not permanently excluded: it is a current collection choice of the
user, not a scope decision of the project, and will be taken up when (if) there is real material
from that domain to design on (see ADR-005).
