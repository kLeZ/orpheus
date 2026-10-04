# Roadmap

Horizons derived from `docs/DESIGN.md` (the purpose) and from the real state of the work (see the local
context file, outside the repository, for the session-by-session detail). It is not a rigid plan: it
is updated when the real state changes.

## Now

- **Defects found by the verification of 2026-10-04** (`docs/TODO.md`): fields lost when rewriting
  some tags, ID3v1 rewrite touching a truncated last frame, folder and tag track numbers that
  differ in two cases.
- **Web interface design** (ADR-0025, proposed): complete application served by `orpheus serve`, Qute
  and htmx, localhost only, English and Italian. Decided so far: scope, process, frontend, access,
  priorities, language, listening, review actions, no archive, persistent decisions. Next: page
  structure and workflows; the English migration it was waiting for is done.

## Next

- Core work the interface needs: review queue actions (ADR-0025, point 8), persistent review decisions
  (point 10), incremental import (today every import needs an empty destination).
- Evaluate the in-place import (item in `docs/TODO.md`: fundamental principle at stake; reflink copies
  on XFS as an alternative).
- A second test library, from another computer of the user's: the thresholds of ADR-0018 and ADR-0023
  are calibrated only on `~/Musica`.
- Review queue priority formula (ADR-004).
- Transaction granularity of `import` and `PRAGMA foreign_keys`.

## Later

- Tier 3b: an LLM fallback for the tracks where not even fingerprinting finds anything, only after the
  algorithmic ways are exhausted (`docs/DESIGN.md`). Most of the 30 tracks left unidentified on
  2026-10-04 are covers with AcoustID clusters split among several bands without a majority.
- A third way for box sets with tags so dirty that not even a more permissive match threshold works
  (e.g. Machine Head 40th Anniversary, 4 CDs).
- TarsosDSP (quality and EQ heuristics, SPEC.md 4.6): no module or dedicated ADR yet.
- Guided replacement (SPEC.md section 6).
- Spotify sync (`orpheus sync-spotify`, built in Python in the past): only once the local library is
  organized stably. Fixed constraint: no authentication to Spotify to read or save audio streams,
  public Web API only.
- Rule engine (macro topic raised by the user on 2026-09-27): the choice rules (tags, placement,
  releases, copies) are more and more a use case for one.

## History

### Corrective plan for the damage of the real run (2026-09-26 and 2026-09-27)

Tier 0/1/2/3a were connected end to end, but the full real run on `~/Musica` of 2026-09-26 showed that
in many cases Orpheus made an already ordered source worse (list of the damage with examples in
`docs/TODO.md`, section "Damage observed"). The most serious damage, Tier 2 assigning releases by other
artists, was fixed first (ADR-0018). The rest was this plan: no new functionality until it was closed.
Each phase was approved, implemented, checked on the metrics and committed separately.

Metrics, computed on the ledger of a run and compared with the baseline of the 2026-09-26 run. The raw
number of fragmented source folders is not a metric: placing the singles of an artist folder correctly
on their albums splits it by construction.

| Metric | Baseline 26/09 |
| ------ | -------------- |
| Tracks in fake albums: files directly in a top-level folder, album title equal to the artist | 278 |
| Files with an `(N)` suffix in the destination | 218 |
| Tier 2 releases by an unrelated artist, excluding declared contrasts | 111 (offline measurement; the ledger of 26/09 did not record the release artist) |
| `Traccia sconosciuta` files | 194 |
| Tracks in `Non Identificato/` with a release id | 41 |
| Tracks with a destination year different from the year in the source folder name | 96 |
| Tracks in albums with different artists, excluding Various Artists and featuring | 10 |

Computed by `LedgerMetrics` in `orpheus-app` (tests), with the report in `RunMetricsReportTest`
(instructions in the javadoc), with 10 sample rows per metric checked by hand on the baseline.

**Phase 0: fast verification loop and corrections already decided.** Every full check cost 41 minutes
of network and 17 GB of copies on a disk at 80%. Result: a dry-run option (then `--simula`, now
`--dry-run`), a cache of external responses (`-Dorpheus.cache-dir=...`, versioned JSON files per
category, not SQLite: it is not domain state),
metrics and baseline, Flyway migration on demand with refusal of a data folder inside the source (no
file created; the default stays the current directory, because the database opens before the command
knows the destination), the same refusal for cache and destination, HTTP listener off (globally: the
server starts before it is known which command runs), `clean` in the pre-push hook, Demo/Live/Bootleg
releases losing like compilations, `fpcalc -ignore-errors` (21 of the 24 failures recovered). On the
6-folder sample: simulated ledger and review queue identical to the real run; with the cache the run
went from 435 to 64 s; "Big Me" moved from the demo "Rough Mixes" (1994) to the album "Foo Fighters"
(1995).

**Phase 1: reliable local evidence** (domain, checkable without network). A single placeholder
recognizer for artist, album artist, album and title; tag sanity (a year counts only as 4 plausible
digits without residues, a track number repeated across files of the same folder is unreliable);
evidence from the path with lower precedence than valid tags (title and number from the file name, year
and album title from the folder name); the album tag inherited only between tracks of compatible
artists; the library root never an album. Result on a network-free simulation of the whole library:
`Traccia sconosciuta` 194 → 0, `(N)` 257 → 185, tracks in albums with different artists 14 → 0,
conflicting years 58 → 28 (all 28 checked legitimate), fake albums unchanged (solved by phase 2). Known
regression: "Concerto For Group & Orchestra" (tag "1969ôñ") lost its year.

**Phase 2: path from the release for identified tracks** (ADR-0020). An identified track leaves the
group and takes its path from its own release; contrasts with the tag go under the identified artist;
`musicBrainzRecordingId` filled in; a review item for unidentified tracks inside a group.

**Phase 3: duplicates, editions and formats** (ADR-0019, rewritten after the user rejected discarding
copies).

**Phase 4: residues and final check.** Mojibake in names, case of the "Unknown Artist" fallback, Tier 3a
coverage, full real run, adversarial review. Result of phases 2-4 (network simulation of the whole
library, first run 34 minutes, the following ones about 4): fake albums 278 → 0, `(N)` 218 → 2
(different songs with the same title, left on purpose), `Traccia sconosciuta` 194 → 0, isolated tracks
with a release in `Non Identificato` 41 → 0, releases by unrelated artists 111 → 0, tracks in albums
with different artists 10 → 0, conflicting years 58 → 28, albums without a year 62 → 28.

### Imports of September 2026

- **2026-09-27, first real import** (`~/Musica-organizzata`, 15 GB): ledger and review queue identical
  to a simulation, source intact, 183 copies discarded under the first version of ADR-0019. Superseded
  the same day.
- **Phase 5 and tagged import** (unsupervised work): copies under the rewritten ADR-0019 (no file
  discarded) and tag writing under ADR-0021. The first run revealed that jaudiotagger overwrote the
  start of the audio of 19 MP3s (ADR-0021, Formats section): library removed, writer fixed, import
  redone. Decoded audio identical to the source for all 2197 copies; 446 files with tag corrections.
- **2026-09-27 evening, with the user's decisions** (tags always consistent with the folder tree,
  ID3v1 aligned, genres from the name search for ordered albums, ASCII instead of typographic
  characters): 2197 ledger entries, no file outside the ledger, folder/tag consistency complete, 1579
  files with corrections, 2132 copies with at least one genre.

### Autonomous correction loop and rule review (2026-10-04)

Damaged files recognized and reported (ADR-0023); Tier 2 rules and placement rules introduced in a loop
of 13 full imports requested by the user, then approved one by one (ADR-0018, "Rules of 2026-10-04");
compilations in Various Artists with PERFORMER and COMPOSER (ADR-0024). Last verification run: source
intact, 30 tracks unidentified (from 46), 15 in "Unknown Year" (from 45), about 120 tracks without a
number inside recognized albums down to 23, 8 albums split in two folders merged, 29 damaged files
reported.
