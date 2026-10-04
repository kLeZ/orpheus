# Orpheus functional specification

## How to read this document

This is a functional specification, not a technical architecture document. It describes what the
software must do and by which rules, not in which language or with which library. The reason is
simple: before building further on the stack of the time (Python, beets, MusicBrainz, AcoustID) we
wanted to be able to judge whether the requirements are complete and consistent, independently of
how they are implemented. Only then does it make sense to ask whether the chosen stack is the right
one, and to be able to redo it in the future without rewriting the requirements from scratch.

When the document names an external service (AcoustID, MusicBrainz, Spotify, YouTube) it is not our
technical choice: these are product constraints already fixed in `docs/DESIGN.md` (fingerprinting
goes through AcoustID, there is never any authentication to Spotify to read the stream, and so on).
No particular client or library for talking to these services is assumed.

The document grew out of a series of spoken discussions, topic by topic, while we checked in
practice what worked and what did not in the implementation written so far. The decisions taken are
collected at the end, in the decision log.

## 1. Mission

Orpheus takes any folder of audio files of mixed origin and turns it into an organized music
library, with correct tags where possible and a consistent folder tree. The expected starting point
is chaos: partial album folders, missing or corrupted tags, isolated tracks without context. This
is the base case to solve, not the exception to tolerate.

Three ways the system can fail, not three success criteria:

1. It recognizes and catalogues little, even without visible errors: a low yield is not a neutral
   outcome.
2. It does not accept the physiological chaos of the input as normal (partial tracklists, corrupted
   tags, mixed origins).
3. It hands to the user what it could solve on its own. The review queue is a safety net and a
   transparency tool, not the primary mechanism by which the system solves difficult cases.

## 2. Domain and input state

Six audio formats are supported, all first-class: MP3, FLAC, OGG, WAV, WMA, M4A. No heuristic or
rule may work only on MP3 and silently degrade on the others. WMA and M4A support is not just
declared: reading and writing tags on both was verified in practice, with the same mechanism used
for the other formats.

A container such as MP4 can hold pure audio (M4A) or video, and what decides is not the extension
but whether a video track is actually present. A file with a video track is never included
automatically: it is reported, and including it requires explicit confirmation; in practice it stays
ignored until the user confirms or rejects it.

There is no guarantee of completeness on the input in general, and the following cases must be
treated as normal, not as exceptions barely tolerated:

- album folders with only a subset of the real tracks, because the user keeps only what they like;
- isolated tracks, with no album folder of any kind;
- missing, empty or placeholder tags, like those left by a ripping tool that never queried an online
  database (titles such as "Track 1", empty artist);
- tags present but wrong or misleading, with a file name that differs from the tag or a release
  different from the one the text suggests;
- non-music files mixed into the same folder hierarchy: what was said above applies to videos, while
  other file types are simply out of scope and need no dedicated rule.

The source folder is never modified or moved: every organization produces a copy in a separate
destination.

## 3. Conceptual data model

However it is persisted, the system must be able to represent four concepts.

A **track** is an audio file with its current metadata (artist, title, album, album artist, year,
track and disc number, external identifiers when known) and a physical path. It also carries a
provenance state: where it comes from, and whether and how it was modified compared to the
original.

An **album** is a group of tracks with shared metadata. It can be complete according to an external
source, partial (a known subset), or absent altogether for an isolated track.

A **ledger entry** is an event recorded for every processed track: from where to where, with which
action (imported enriched, imported as is, discarded as duplicate, reconciled, reconstructed...),
why, and with what confidence. It must always be possible to reconstruct the full history of an
organized file, not only its final state.

A **review item** is a unit of the review queue: a track, a problem category (audio quality,
suspected edit, tag correction, uncertain identification, unidentified...), a readable reason, a
confidence and a status (pending, confirmed, to replace, replaced, rejected...).

## 4. Cataloguing pipeline

### 4.1 Inventory

Before any change the system produces a non-destructive census of the source: how many audio files,
which ones lack key fields such as year or album, which non-audio companion files are present. It
serves to size the work, not yet to decide it.

### 4.2 Artist folder deduplication

Folders with different names that refer to the same artist, because of a typo, different
capitalization or an alias, must be detected and proposed for a merge, checked against an external
catalogue when possible. The merge is always confirmed, never done silently: a wrong name can merge
two artists that are actually distinct.

### 4.3 Import and destination structure

Every source file is copied, never moved or deleted in the source, to a destination with a
predictable tree: artist / year - album / disc if any / number - title. Tags are corrected when the
identification allows it. A track whose identification produces no album still stays traceable: it
must not disappear silently into a generic container without leaving an item in the review queue
(see 4.4, 4.5 and section 5).

When an album has tracks performed by different artists, whether detected through external
identification or through the local rule described below, the album artist becomes the
conventional value "Various Artists", treated like any other artist for the folder tree and not as
a separate category: `Various Artists / year - album / number - track artist - title`. Only in this
case does the file name also include the artist of the single track; the track artist tag is always
filled in regardless of the file name, so that performers can be told apart without opening every
file.

When external identification is not available and only local tags are used (Tier 0), a group is
treated as a compilation if at least two different, non-empty track artists appear in the same
grouping (same folder, same album tag).

### 4.4 Identification in increasing tiers

Identification proceeds in tiers, from the cheapest and most deterministic to the most expensive,
stopping at the first one that produces a usable result.

**Tier 0**, the structural fallback: if the tags are not enough, the source folder structure (the
folder name as the presumed album) is still a valid grouping signal.

**Tier 1**, clustering by tags: tracks with consistent album tags spread over several source folders
are recognized as the same album and reconciled into a single destination.

**Tier 2**, identification by audio fingerprint: for a track without a recognized album, either
because Tier 0/1 produced nothing or because they produced a grouping with empty or placeholder tags
(an album assigned by a folder fallback is not a real identification), the system computes an audio
fingerprint, compares it with a public fingerprint database and, for each identified recording,
retrieves the known releases it appears on. The choice among several possible releases follows two
rules in order of priority:

1. A release of type Album always beats a release of type EP or Single, whatever the release date. A
   single or an EP that anticipates the album, common practice in the record industry (same master,
   worked on in the same time window), is not a distinct recording: if there is at least one Album
   among the candidate releases, EPs and Singles are discarded and rule 2 applies among the Albums
   only; if the recording never ended up on an Album, rule 2 applies to all available releases.
2. Among the remaining releases the chronologically oldest wins. A date known only by year, without
   month and day, must not beat a more precise date in the same year or a later one: a chronological
   comparison that ignores the precision of the date is a defect to fix, not a legitimate ambiguity
   to report to the user.

Posthumous reissues of the same master and remasters already fit this scheme without a dedicated
rule, because they are chronologically later than the original release: once date precision is
handled, rule 2 excludes them on its own. New recordings, when a song is recorded again with a
different line-up or instrumentation, create no ambiguity: they produce a different audio
fingerprint, and so a different recording, not a choice among releases of the same recording.

Whatever result is chosen, it always stays in the review queue for a visual confirmation: it is
never applied without leaving a trace.

**Tier 3**, the residual fallback: for the tracks that not even the fingerprint can identify,
because the recording is not in the database used, a last-resort mechanism, probably assisted by a
language model for the text classification of the file or folder name, tries to suggest a
classification, always subject to confirmation. If this also fails, the track stays explicitly
reported as unidentified, not hidden.

### 4.5 Multi-track consensus

The mechanism applies only to groups of tracks with placeholder tags, the same signal already used
for the single-track Tier 2 gate: an empty or whitespace-only artist, that is tags never really
written. It does not apply to any folder with several tracks: a folder with real tags, even partial
or spread over several artists, is already Tier 1's domain. Nor does it apply to a large folder
with real tags just because it is not yet organized into albums: if the user has already split CD1,
CD2 and so on by hand for a box set, that split is a choice made upstream, not an ambiguity for us to
solve.

The rule is this: each track of the group is first identified individually with the Tier 2 rules.
If at least 65% of the tracks converge on the same release, the whole group is forced onto it,
including the minority tracks provided they also have a match, not necessarily the best, on that
release, on the exact same release and not just the same title: when there are several releases
with the same title, rule 2 of Tier 2 applies again, the oldest among the winners. A minority track
with no match at all on the winning release remains a genuinely distinct track, with its own
individual identification.

If no release reaches 65% consensus, each track goes back to the individual Tier 2 behaviour. There
are no intermediate levels of sub-groups for now: that is a refinement that can wait, not a
requirement of the first version.

One exception is knowingly accepted: a release with truly exclusive content, for example a remix
present only on the single, can stay alone in the single's folder while the base version of the
same track is forced into the album. It is a minor imprecision, but preferable to the alternative:
treating that track as a duplicate and discarding it, losing the only version owned if the album
version was never acquired.

### 4.6 Quality problem detection

Independently of identification, every imported track is analysed for encoding artifacts or
corruption, for an abnormal duration compared with the reference recording (a possible edit, cut or
different version from the expected one), and for spectral anomalies compared with other tracks of
the same artist. Every detection produces a review item, never an automatic action on the file.

## 5. Review queue

Its function is to be a safety net and a transparency tool on what was done or proposed
automatically. It is not the primary mechanism by which the system solves difficult cases: if a
plan for a difficult case boils down to reporting it and letting the user decide, it is not a
complete plan.

Every review item has a category (audio quality, suspected edit, abnormal EQ, applied tag
correction, Tier 2 reconstruction, unidentified...), a readable reason and not just an internal
code, a status, and a set of available actions that depend on the category: a tag correction has no
better version to look for, while a Tier 2 reconstruction needs an action to undo it if wrong,
bringing the track back to its previous state.

Every review item also carries a raw signal, specific to its category: the distance of an applied
match, the deviation ratio of a duration, a spectral anomaly score, the match score of an audio
fingerprint. This value always stays available and queryable on its own, category by category: it
must not be squashed into a single generic number that erases its original meaning.

Above these raw signals there is a second, derived value, which we call priority: a continuous
number, not in bands, designed to be comparable across categories and used to sort the whole queue.
It is obtained by expressing each raw signal as a confidence (how sure we are that there is nothing
to correct, a value between 0 excluded and 1, where 1 is absolute certainty and values close to 0
are an almost total absence of certainty) and then applying the same idea behind Shannon's
information content, the same one behind inverse-variance weighting in statistics: the priority of a
review item is the weighted sum of the negative logarithms of these confidences,

```text
priority = - Σ (weight_i · log(confidence_i))
```

summed over the signals that apply to that review item, usually just one. When a confidence tends to
zero, the priority diverges to infinity by construction of the formula itself, with no hand-written
exception for the limit case. That is exactly the intended behaviour for a track that stayed entirely
unidentified: zero signal, maximum priority, not by an if-else but by a mathematical limit. In
practice, such a function needs a minimum value other than zero to represent "no certainty" without
producing an infinite or undefined value that cannot be handled: that remains an implementation
choice of the formula, not a change of the model.

The system must also make it possible to look up, for every organized track, its complete history of
ledger entries, not only its current state, and to search or filter by track, artist or free text.

## 6. Guided replacement flow

For a track reported as problematic, for quality or a suspected edit, whose original record the user
physically owns, the system proposes replacement candidates from an external source (YouTube today)
and allows comparative listening before deciding. The actual replacement always remains an explicit
manual step: the original is archived, never deleted, and the candidate takes its place only after
confirmation.

## 7. Synchronization with streaming services

Today this means Spotify: the system compares the organized local library with the remote state, for
example the favourite songs, using only public search and playlist management APIs. The constraint
is fixed: no authentication to read or write the audio stream. By default the system only shows what
would change, in dry-run mode, and applies writes only on explicit request.

## 8. Cross-cutting requirements

Running an operation again must not duplicate entries, corrupt the state, or require the user to
clean up by hand before running it again. No cataloguing operation writes, moves or deletes anything
in the original source folder. Every file in the organized library must be able to answer the
question "how did you get here, and why" without guessing from the final state. Any action taken
without upfront human confirmation, such as a Tier 2 reconstruction or a tag correction, must be
undoable after the fact. And no requirement of this specification can be considered met if it holds
only for MP3: it holds for all six formats of section 2.

## 9. Explicitly out of scope

Out of Orpheus's scope, for now: authenticating to Spotify to read or save audio streams, a
permanent constraint and not just a current one; fully automatic resolution of the residual Tier 3
cases without any confirmation, since even the possible assistance of a language model stays
subject to review; and a third way for releases or box sets with metadata too ambiguous even for
Tier 0/1. That is not a Tier 2 case, because it is not an orphan but a complete album with an
uncertain release, and it remains a problem to be handled separately, not yet specified.

## Decision log

The decisions taken while reviewing this specification are collected in `docs/adr/`, one per file,
in the style of a decision record: they are not architecture decisions in the strict sense, but the
form (context, decision, consequences) works just as well for keeping track of them over time.

- [ADR-001: Release selection and multi-track consensus in Tier 2](adr/0001-release-selection-and-multi-track-consensus.md)
- [ADR-002: Supported audio formats and files with a video track](adr/0002-supported-audio-formats-and-video-files.md)
- [ADR-003: Compilations, Various Artists and general destination pattern](adr/0003-compilations-various-artists-and-destination-pattern.md)
- [ADR-004: Priority of review items in the review queue](adr/0004-review-item-priority.md)
- [ADR-005: Classical music postponed, not excluded](adr/0005-classical-music-postponed.md)
