# Design: what Orpheus must do, and why

This document fixes the purpose of Orpheus so that it is never lost again during development. If a
technical decision seems to contradict what is written here, it is the technical decision that has
to change, not this document, unless the user explicitly changes the purpose.

## Mission

Orpheus takes any folder with any music in it, including files of unknown or mixed origin and
whatever may have been downloaded from YouTube or other sources over the years, and turns it into a
real digital music library: comfortable, clean and tidy, with ID3 tags fixed where the data exists
and a consistent folder tree.

**Formats are not only MP3.** FLAC and OGG are first-class, not side cases: they are more and more
often the format of the digital copy that comes with a physical record, usually FLAC at the highest
quality available at production time. Every heuristic, scanner or pipeline written for this project
must work on all supported formats, not only on the one it was first tested with.

**Guiding metaphor**: Orpheus must be able, figuratively, to put its hands in the mud and pull
perfect music libraries out of it, like a patient vinyl restorer who walks into the house of an old
music lover and catalogues the collection with care.

## What Orpheus is, first of all

Orpheus is **an investigative archivist**. The expected starting point is not "a half-organized
library to tidy up": it is no reliable information at all, just a heap of "stuff" piled up, and
plenty of time to catalogue all of it patiently. If a task looks solvable by a generic tool like
Picard without custom logic, that task is probably not the point of Orpheus: the point is the cases
a generic tool leaves behind.

## Failure criteria

These are the criteria for judging whether a part of Orpheus does its job. Not success criteria:
the ways it **fails**.

1. **It recognizes little.** A low identification and cataloguing yield is a failure even without
   visible errors; "I found nothing" is not a neutral outcome.
2. **It does not accept the physiological chaos of the archives it is given.** Partial album folders
   (the user keeps only the tracks they like from a record: no CD drive on the laptop, no wish to
   load a disc again to hear one or two songs), missing or corrupted tags, mixed origins, isolated
   tracks whose original record the user sometimes no longer remembers: **this is the base case to
   solve, not an edge case to barely tolerate.** If a part of the system stops working well on
   incomplete data, it has failed its job.
3. **It hands too much back to its user.** Asking for confirmation or a choice on everything means
   not doing the work Orpheus was designed for. The review queue (`/review` in the web UI) is a
   safety net for residual cases and a transparency tool for the changes applied: **it is not the
   primary resolution mechanism.** If a solution mostly moves the decision to the user instead of
   taking it, it is a partial solution, not a complete one.

## Approach: algorithmic first, agent loop only when needed

The patient cataloguing work an expert human would do can be automated today with an agent loop (an
LLM reasoning about the residual cases). But it has to be done **as algorithmically and
deterministically as possible**: audio fingerprints (AcoustID/Chromaprint), MusicBrainz searches,
heuristics on text and folder names, explicit and verifiable rules. The agent loop is reserved for
the residual cases the algorithmic approach cannot solve on its own (for example ambiguous text
classification of tracks with no match at all); it is not the first line nor the default
mechanism.

## Practical corollaries (never to be forgotten)

- Incomplete album folders are not "damaged" data to be repaired as far as possible: they are the
  user's intentional curation, and must be treated as normal input, not as an exception.
- An isolated track without album context must be identifiable (by fingerprint or another
  algorithmic means) and placed back on its most plausible original album, not dropped into a
  "Non-Album" heap because the tracklist is incomplete.
- When identification really fails (no match possible by any algorithmic means, e.g. a recording
  missing from the databases used), the track must be clearly reported as such: not hidden, and not
  passed off as "handled".
- The constraint of never authenticating to Spotify to read or save audio streams (public Web API
  only) stays unchanged; see the README.
