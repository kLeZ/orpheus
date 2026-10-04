# ADR-0019: Copies of the same track, different editions and alternative formats

**Status**: accepted, 2026-09-27, in the version rewritten after the user's decisions. The first
version (a single copy in the library, the others discarded) was rejected: "everything goes into the
library, we cannot leave data out. Data must be preserved; Orpheus's role must be to reorganize what is
there, not to clean up".

## Context

On the real run of 2026-09-26 the destination contained 218 files with a `(N)` suffix: different
source files that arrived at the same path. The real cases are of three kinds:

- the same release present several times, in different encodings: almost every Deep Purple album
  exists twice (`Burn/` and `Deep Purple - 1974 - Burn/`), 12 tracks out of 12 with the same numbers;
- the same release in different formats: `Myr/2013 - Days Of Convergence` in FLAC, MP3 and OGG;
- different editions with a different tracklist under the same album tag: `Remedy Lane` (13 tracks)
  and `Remedy Lane (Japanese Edition)` (14), the same for Entropia and One Hour By The Concrete Lake.

No previous decision in the SPEC or in the ADRs covers these cases. The principle of `fec8012` (never
discard a track without comparing its content) still holds: different encodings are never identical
byte by byte, so the comparison must be on the identity of the track, not on the bytes.

## Check on the data

Comparison of the decoded audio (hash of the samples with `ffmpeg -f md5`, independent of the tags) on
the 183 pairs the first version of this ADR discarded:

- 165 have identical audio: the same file saved twice, with a few bytes of different tags. Almost all
  are Deep Purple albums present in two folders (`Burn/` and `Deep Purple - 1974 - Burn/`, Bananas, In
  Rock, Fireball, Now What, the four parts of Machine Head 40th Anniversary...), plus one track by
  "Latte e i suoi derivati" and 3 by Ray Charles;
- 14 are the same track in different formats (Myr "Days Of Convergence" in FLAC, MP3 and OGG);
- 4 have the same format, position and title but different audio (Nickelback "How You Remind Me"
  twice, a Red Hot Chili Peppers track, one from Remedy Lane, Sonata Arctica "Fullmoon" with a
  different duration): different versions.

Albums the user remembers as different (original and compilation, original and the 40th anniversary
remaster, re-recordings) have different album tags and were not among these pairs: they stay distinct
albums as before.

## Decision

Every source file always goes into the library. When several files would arrive at the same path:

1. **Identical audio** (same hash of the decoded samples): all in the library, from the second one on
   with the counter `(NN)` in the name; an "identical duplicate" review item in the database, so that
   the CLI and the web interface show them ("if one has duplicates they know it, or want to know
   it").
2. **Different formats** of the same track: side by side; the different extension is enough to tell
   them apart, no suffix.
3. **Same format, different audio**: all in the library, from the second one on with `(version NN)`
   in the name; an "alternative version" review item with the durations of the files involved.
4. **Different editions**: when Tier 1 merges folders with the same album tag, a folder whose name
   carries an edition qualifier ("Japanese Edition", "Limited", "Deluxe", "Bonus", "Expanded",
   "Special", "Anniversary", "Remaster") and contains songs the others do not have becomes a separate
   album, with the qualifier in the title ("Remedy Lane (Japanese Edition)").

The source is never modified (SPEC.md section 2).

## Alternatives considered

- **A: everything in the library, separate folders per edition and format**: no suffix, but duplicated
  folders for every copy; more noise than a suffix within the same album.
- **B: a single copy per track** (first version of this ADR): rejected by the user, it loses data.
- **`(NN)` suffix for different versions too**: discarded by the user in favour of `(version NN)`,
  which tells a duplicate from a different version at a glance.

## Consequences

- The library contains exactly the files of the source, reorganized.
- A hash of the decoded audio is needed for every colliding file: a full decode, expensive but limited
  to collisions.
- **An import must go into an empty destination** (limit recognized on 2026-09-27). The comparison
  that decides whether a file is already there looks at the content: after tag writing (ADR-0021) a
  copy is no longer identical to its source, so a second import into the same destination, or
  resuming an interrupted import, puts a " (2)" copy next to every file already copied. To repeat an
  import, empty the destination and the data folder; an incremental import would need to recognize
  the files already imported from the ledger (item in `docs/TODO.md`).
