# TODO

Point-by-point task status. For the reasons behind each item see the local context file (outside the
repository); for the overall picture see `ROADMAP.md` and `DESIGN.md`.

Code identifiers use the names of the glossary in `docs/ENGLISH-MIGRATION.md`, ahead of the code
rename. The historical sections at the bottom are condensed; the original Italian text is in the git
history (before ADR-0022 phase 2).

## Open

- [x] Autonomous correction loop of 2026-10-04 (repeated full imports of `~/Musica`, details in
      ADR-0018 "Rules of 2026-10-04" and ADR-0023): `Unidentified` from 46 to 30 tracks, "Unknown
      Year" from 45 to 15 tracks, tracks without a number inside recognized albums from about 120 to
      28, 8 albums split in two folders merged, covers and mislabeled songs placed by fingerprint
- [ ] The 30 tracks left in `Unidentified`: mostly punk covers with AcoustID clusters split among
      several bands without a majority ("Stand By Me", "Walking On Sunshine", "Brown Eyed Girl") and
      songs missing from AcoustID; candidates for Tier 3b
- [x] (2026-10-04, ADR-0018 rule 11) Nirvana "Sliver" placed on the Paramount live of the "Nevermind"
      bonus disc: now on the 1990 single
- [ ] One-track albums with a year and a "junk" album tag ("www.guanoapes.org", "Pop 2000", "Upcoming
      Album"): they stay albums. The user: "www.guanoapes.org" is the fan community the track
      circulated in, "Pop 2000" the compilation it was taken from; they are provenance, not errors
- [x] (2026-10-04, ADR-0022 phase 8) Redo the import into `~/Musica-organizzata` with the
      MusicBrainz album titles, separate editions and English placeholders: 2197 copies, source
      intact, ledger identical to the dry run
- [ ] Found by the full check of 2026-10-04 (present before the English migration too, checked by
      importing the same files with the code of `42dae67`):
      - 8 MP3s whose last frame is truncated into the ID3v1 tag: the MPEG bytes are identical, but
      rewriting ID3v1 (genre byte) changes the last few milliseconds decoded. Writing ID3v1 only when
      the last frame is complete, or not touching it in that case, would avoid it;
      - fields lost when rewriting some tags: `TCOP`, `TENC`, `TOPE` and 4 more frames of "Break
      Darling Break" (ID3v2.3 source), `WM/Composer` and `WM/Lyrics` of a WMA (Cristina D'Avena);
      the writer must keep every field it does not write (ADR-0021, rule T25);
      - 2 tracks with a folder number different from the tag (blink-182 "What Went Wrong" 14 against
      1, Manowar "Hail And Kill" 8 against 7): the tag number, repeated in the folder, is discarded as
      unreliable and the path takes the release position, but the FILL rule T14 keeps the original
      tag. Folder and tag must say the same thing (ADR-0021, principle 1)
- [x] (2026-10-04) English migration and publication (ADR-0022, `docs/ENGLISH-MIGRATION.md`): all
      nine phases done, published at https://github.com/kLeZ/orpheus under AGPL-3.0-or-later
- [x] (2026-10-04: release-group year found by name when the tracks disagree) Album year on a tied
      vote: the oldest wins. "Queens of the Stone Age/Lullabies to Paralyze" has one 2004 track and
      one 2005 track and ends up in 2004
- [ ] Incremental import (priority for real use, user on 2026-10-04): today an import always needs an
      empty destination (limit documented in ADR-0019 and in the README), so adding ten files means
      redoing the whole library. It needs to recognize from the ledger the files already imported
      (path, size, modification time, audio hash) and place only the new ones, keeping in mind that
      a new file can change decisions taken on the others (consensus, split albums, scattered
      compilations)
- [ ] Review queue actions (core functionality, prerequisite of the GUI; SPEC.md section 5: "a set of
      available actions that depend on the category"): accept, restore the original tags
      (`ORPHEUS_ORIGINAL_*`), undo a Tier 2 reconstruction bringing the track back to its previous
      state, move a track to another release, mark a damaged file as replaced. Every action writes a
      ledger entry and deletes nothing
- [ ] In-place import (user request of 2026-10-04, to be evaluated: feasible, or a hard design
      choice). Today a founding principle is that the source is never touched (SPEC.md section 2,
      DESIGN.md), and many safeguards rely on it. First evaluation:
      - reorganizing by moving files inside the source needs an undo journal (the source/destination
      ledger already exists), handling of paths occupied by files not yet moved (also in chains)
      and tag writing on the originals: a crash halfway leaves a mixed library, and the jaudiotagger
      bug of 2026-09-27 would have damaged originals instead of copies;
      - less risky variant: import into a new folder, verify, then swap it with the source (the
      original source stays as an archive until the user deletes it); twice the space is needed
      only temporarily;
      - `~/Musica` is on XFS, which supports reflink copies (`cp --reflink`): a copy takes space only
      for the modified blocks (the tags), so a copying import would cost almost no space without
      touching the source. To check: that the file system was created with reflink enabled, and
      whether this removes the practical reason for in-place
- [ ] Second test library (the user has at least one more, on another computer, to be retrieved):
      the thresholds of ADR-0018 and ADR-0023 are calibrated only on `~/Musica`, with 24 FLAC and 14
      OGG files
- [ ] Check that MPD and Amarok read the TMCL frame as performer (ADR-0024)
- [ ] Review interface redesigned from scratch: designed before the English migration, implemented
      after it (user decision of 2026-10-04, ADR-0025 proposed). The Python web UI was removed with
      `44146f0`, the empty Java module `orpheus-web` on 2026-09-27. Still to decide: page structure
      and workflows
- [ ] Macro topic raised by the user on 2026-09-27: the choice rules (tags, placement, editions,
      copies) are more and more a use case for a rule engine. Later
- [ ] "Concerto For Group & Orchestra" (Deep Purple, 5 tracks): year tag "1969ôñ" now discarded as
      malformed (rightly), same residue as "1982ôñ" of Blue Light (wrongly); to review together with
      the mojibake handling
- [x] Damaged audio files (ADR-0023, 2026-10-04): fingerprint from the decoded audio when fpcalc
      fails, a new lookup with the decoded duration when the declared one finds nothing, AcoustID
      score as confidence, integrity analysis of every file reported as `AUDIO_QUALITY` (decoding
      errors, truncation, drops to digital silence), the intact copy gets the name without a suffix.
      Measures and thresholds in the ADR; the Java analyzer was checked on the 2204 files against the
      calibration prototype (same 44 files reported or not analyzable)
- [x] (2026-10-04, ADR-0023 point 6) New AcoustID query with the duration of the recording found by
      artist and title, for CBR MP3s and truncated OGGs that find nothing with their own duration;
      the duration filter of `CandidateRecordingFilter` adjusted with it
- [ ] Line noise superimposed on the music (hiss, "receiver picked up"): no detector; a click
      detector tried during calibration did not separate it from compressed rock
- [x] (2026-10-04, ADR-0023 point 7) Evanescence "Farther Away": confirmed by the user, phone line
      tones at 0:59 and later; dedicated SIT detector. Pain Of Salvation "Nightmist" ("Entropia")
      stays a known false positive of the drop detector
- [ ] About 35 Sentenced MP3s with a Xing header declaring a wrong duration (intact audio): players
      show the wrong duration. Fixable by rewriting the header in the copy
- [ ] 12 Nashville Pussy "High As Hell" MP3s in a RIFF/WAVE container preceded by an ID3 tag: ffmpeg
      and fpcalc do not open them (they do after skipping the ID3 tag), so no integrity analysis and
      no fingerprint. They are not damaged
- [ ] "Seether Broken feat Amy Lee" (`Evanescence/`, unidentified): not damaged, it fades out at
      161 s. AcoustID recognizes it at 1.0 as "Broken" by Seether, discarded by the artist constraint
      (Evanescence tag) and by the duration (257 s for the recording)
- [ ] Bon Jovi "Always" placed on a live version ("These Days", 1995): the studio version appears only
      on the "Cross Road" compilation, penalized as a compilation. Comparing the duration of the chosen
      recording with the file would fix it
- [ ] Mislabeled tracks whose title differs from the recording ("Hokuto no ken shiro 7" = "Tough Boy"
      by TOM★CAT, "I'm not sick but I'm not well" = a line of "Flagpole Sitta"): a contrast with the
      tag now requires the title, so they stay unidentified
- [ ] Rule for files with a video track in generic MP4 containers without an explicit `.m4a`/`.mp4`
      extension (ADR-002); the audio/video discriminator is already implemented and checked in
      `JaudiotaggerTagReader` for the recognized containers
- [ ] Review queue priority via a weighted sum of log-confidences (ADR-004)
- [ ] MusicBrainz `"YYYY-MM"` dates (year and month, no day) are downgraded to a year-only
      `ReleaseDate` by `MusicBrainzReleaseLookup`, because the domain does not model this
      intermediate precision; a fake day would bring back the bug of ADR-001. To reconsider only if
      `ReleaseDate` gets a third precision
- [ ] Transaction granularity of `import`: every `record()` of ledger and review queue is its own
      transaction (not one batch), and the file is copied before the entry is written. A partial
      state is acceptable (the source stays intact), but a crash between the two writes for the same
      isolated track leaves an entry without its review item, or an orphan file in the destination
      without a ledger row. Found by the final adversarial review, medium severity, no data loss
- [ ] `PRAGMA foreign_keys=ON` not set on the SQLite connection (community dialect): the foreign key
      from raw signals to review items is not enforced at runtime. Irrelevant while the database is
      write-only, to fix before more complex queries
- [ ] TarsosDSP module and ADR (quality and EQ heuristics, SPEC.md 4.6): deliberately left out of the
      initial scaffolding
- [ ] End-to-end regression baseline from the known real cases (Deep Purple "Burn", Machine Head 40th
      Anniversary, Skunk Anansie, Bananas, Concerto), using the behavior of the old Python pipeline
      (via `git show`) as an oracle
- [ ] Tier 3b (ADR-0017; the deterministic Tier 3a is done): an LLM as last resort, only after the
      algorithmic ways, for tracks where not even fingerprinting finds anything. A genuine real
      example is still needed: "Because of you", cited in earlier Python sessions, turned out to be a
      false negative of the shared AcoustID key bug
- [ ] Web UI: page for the manual resolution of Tier 3 residues
- [ ] A third way for box sets with tags too dirty even for a permissive match threshold (e.g.
      Machine Head 40th Anniversary, 4 CDs)

### Damage observed on the real run of 2026-09-26 (source/destination comparison)

The source `~/Musica` is largely already ordered by `Artist/Album`; in several cases the output of
Orpheus was worse than the source. Analysis done by mapping the ledger (source to destination) per
folder: 45 source folders out of 467 ended up fragmented across several destinations. All the items
below were closed by the corrective plan (`ROADMAP.md`, History).

- [x] (ADR-0018, checked on the whole library on 2026-09-27) Tier 2 assigned releases by other
      artists. Confirmed cause: contamination inside AcoustID clusters at score 1.0, not separable by
      score. Offline measurement on the 329 reconstructions: 111 wrong out of 316 with an artist tag
      (71 unrelated artists, 40 compilations). Fixed with the candidate recording filter in the domain
      (`CandidateRecordingFilter`) and by excluding compilations and other artists' releases from the
      release selection while something else remains
- [x] (phase 2) Tag/fingerprint contrast (ADR-0018 point C): only release and year were applied, the
      file stayed in the folder of the tag artist (`Beatles/1969 - Beatles/Sugar Sugar.mp3` with
      release The Archies)
- [x] (phase 0, 2026-09-26) Release selection: Demo, Live and Bootleg releases won when they were the
      oldest left after ADR-0018 ("Big Me" on the demo "Foo Fighters Rough Mixes", 1994; "What'd I
      Say" on "Ray, Rare and Live", 2003). Soundtrack cannot be excluded as a block ("Help!" by the
      Beatles is a Soundtrack on MusicBrainz)
- [x] (phase 0, 2026-09-26: `clean` in the pre-push hook) Incremental Maven build: after changing the
      signature of `FingerprintLookupPort`, `mvn test-compile` without `clean` gave BUILD SUCCESS with
      stale test classes; only `mvn clean test-compile` showed the errors
- [x] (phase 2, ADR-0020) Artist folder with loose singles treated as an album named after the
      artist: e.g. `Red Hot Chili Peppers/` (17 tracks from different records, a sensible curation)
      became 11 folders `YYYY - Red Hot Chili Peppers`, one per year of the Tier 2 release. Same
      pattern on about 40 folders (ACDC, Aerosmith, Ray Charles, Foo Fighters, Bon Jovi, Elvis,
      Beatles, Iron Maiden, Metallica, Nirvana, System Of A Down, Incubus...)
- [x] (phases 1-2) Artist lost or wrong: `Linkin Park/` (22 tracks) to `Artista sconosciuto/...`,
      `Skunk Anansie/` to `Artista Sconosciuto/...`, `Dream Theater/` to `Various Artists/...`. The
      parent folder name was not used as a fallback for the artist
- [x] (phase 1, 2026-09-26) Different artists merged: the 14 loose files in the root of `~/Musica`
      (Sonata Arctica and Elio e le Storie Tese) all ended up in `Sonata Arctica/YYYY - L'album
      Biango/`, with Elio's album title, Sonata Arctica as artist and 3 different years
- [x] (phase 3, ADR-0019) Different editions and formats merged into one folder with `(2)` suffixes:
      `Remedy Lane` and `Remedy Lane (Japanese Edition)` in one 28-track folder (same for Entropia
      and One Hour By The Concrete Lake); `Myr/Days Of Convergence` in MP3, FLAC and OGG in one
      21-file folder; Deep Purple, with almost every album twice in the source, merged with copies
      like `01 - Burn (2).mp3`
- [x] (phase 1, 2026-09-26) Information present in the source and lost: year in the folder name
      ignored (`Deep Purple - 1971 - Fireball` to `Anno sconosciuto - Fireball`), wrong year (`Deep
      Purple - 1987 - The House Of Blue Light` to `1982 - ...`), truncated year (`197 - Made in Japan-
      25 Anniversary...`), title in the file name ignored (194 `Traccia sconosciuta`)
- [x] (phase 4: truncated ID3v1 residues in the tags) Folder names with mojibake in the source
      (`Concerto For Group & Orchestra{__ôñ`) carried into the destination, and two destination
      folders that looked identical for the same album
- [x] (phase 0, 2026-09-26: explicit refusal, migration on demand, no file created) The default of
      `orpheus.data-dir` was the current directory: launching `import` from inside the source wrote
      `orpheus.db` into the source, which must never be modified. It really happened in the run of
      2026-09-16 (`~/Musica/orpheus.db`, 2197 entries, deleted on the user's request after extracting
      the counts)
- [x] (no longer needed, 2026-09-27: "Ecliptica" brought together by consensus and ADR-0020)
      Retroactive cross-folder reconstruction (like `reconstruct-orphans --apply` of the old Python
      pipeline). Multi-track consensus does not merge different groups or folders by itself
- [x] (phase 2, ADR-0020) Destination path of an isolated track identified by Tier 2: it stayed under
      the unidentified folder instead of a real "year - album title", because `Release` carried no
      title. On the full run 41 of the 47 isolated tracks had a real MusicBrainz release id from
      Tier 2 and still ended up there: the identification existed and was lost in the output
      (failure criterion 1 of DESIGN.md)
- [x] (phases 1-2) 194 destination files named `NN - Traccia sconosciuta` (126 reconstructed by Tier
      2, 63 imported as is, 5 reconciled): no title tag, and the title recovered neither from the
      file name (`09 - Deck The Halls.mp3`) nor from the MusicBrainz recording when Tier 2 had found
      it
- [x] (phase 2) `ExternalIds.musicBrainzRecordingId` not filled in when Tier 2 identified a track
      (only `musicBrainzReleaseId`): `Tier2Candidacy` flattened the releases of several candidate
      recordings without keeping which one produced the winning release; a declared simplification
      of the session that introduced Tier 2 (2026-07-24)
- [x] (phases 1 and 4) Case normalization between the "Unknown Artist" fallback and real tags with a
      different case (`DestinationPath`): sibling folders for the same concept. Found by the final
      adversarial review on the real run, low severity
- [x] (phase 1, 2026-09-26) `FileNameHeuristic` (Tier 3a) with several ` - ` separators and a leading
      dash: `Lagwagon/ - Take a look around - Punk cover.mp3` gave artist `- Take a look around`,
      title `Punk cover`. The heuristic did not use the parent folder name as artist
- [x] (phase 0, 2026-09-26) `FpcalcClient`: 24 `fpcalc` failures on the full run (21 with code 3, 3
      with code 2), all MP3s with corrupted frames. `fpcalc -ignore-errors -json` (an option not
      listed in `fpcalc -h` 1.6.0 but accepted) gives a valid fingerprint: 21 recovered, the 3 with
      code 2 ("Empty fingerprint") not
- [x] (phase 0, 2026-09-26: `quarkus.http.host-enabled=false`) The `import` command also started the
      Quarkus HTTP listener on `0.0.0.0:8080` for the whole run: a port open to the network for no
      reason during a CLI command

## Done (session of 2026-09-26: build verified again and full real run)

- [x] Reactor build green, 193 tests, after repopulating Quarkus deployment artifacts missing from
      `~/.m2` (a local cache problem, not a code one)
- [x] Full real run Tier 0/1/2/3a on `~/Musica` (2204 audio files, real AcoustID key, public
      MusicBrainz): 41 min 29 s, exit 0. 7 unreadable, 0 copy errors, 389 albums, 2150 grouped, 47
      isolated, 2197 ledger entries (1358 imported as is, 510 reconciled, 329 reconstructed by Tier
      2), 342 review items (329 Tier 2 reconstruction, 7 audio quality, 6 uncertain Tier 3a
      identification)
- [x] Source checked intact (path, size and mtime before and after, 2709 files)
- [x] Determinism: ledger identical row by row (source, destination, action) to the run of
      2026-09-16, same code and same AcoustID/MusicBrainz answers 10 days apart

## Done (session of 2026-09-16: Tier 3a and cascade orchestration)

- [x] ADR-0017: Tier 3a as a deterministic text heuristic on the file name, Tier 3b (LLM) explicitly
      postponed; the 0, 1, 2, 3 orchestration moved from the CLI into the domain
      (`IdentificationPipeline`, `TrackOutcome`)
- [x] `FileNameHeuristic` with `TextClassificationPort`: active only when Tier 0/1 does not group and
      Tier 2 finds no match; never applied automatically, it produces `UNCERTAIN_IDENTIFICATION` with
      a text classification confidence signal
- [x] Real run on the whole library the same day, not documented at the time: reconstructed on
      2026-09-26 from the ledger left in `~/Musica/orpheus.db`, same counts as the run of 2026-09-26

## Done (session of 2026-07-24: multi-track consensus of ADR-001 and Tier 2 end to end)

Note of 2026-09-26: this section was marked as committed, but all the related code was still
untracked in git.

- [x] Tier 2 identification of a single track (`Tier2Identifier`, `Tier2Candidacy`): candidate
      fingerprints, releases for each, `ReleaseSelector` on the union
- [x] Multi-track consensus (ADR-001, SPEC 4.5, `MultiTrackConsensus`): dedicated eligibility gate,
      65% threshold in integer arithmetic, safeguard on the forced minority tracks, tie-break for
      releases tied at the top. It works only inside a single `Album` produced by Tier 0/1, by design
- [x] AcoustID adapter: real `fpcalc` invocation (never a propagated exception), Quarkus REST Client,
      own rate limiter, key from configuration (never hardcoded, ADR-014). `fpcalc` does not
      fingerprint pure silence: test fixtures need a real tone
- [x] MusicBrainz adapter: recording lookup (response shape checked with real calls), User-Agent
      overridable from the CLI, its own rate limiter
- [x] Wiring in `ImportCommand` and the `--musicbrainz-user-agent` option (generic value in the
      committed file, overridable by CLI, environment or external config; no personal data
      committed). Without an AcoustID key the existing Tier 0/1 path is unchanged
- [x] 4 end-to-end integration tests with deterministic fakes, no network
- [x] Targeted real run on the user's "Sonata Arctica" folder (13 tracks): 9 identified with real
      MusicBrainz release ids, the 11-track placeholder group not forced because the observed
      convergence (36%) is under the 65% threshold, one `fpcalc` failure handled, source intact

## Done (session of 2026-07-23, third round: Tier 0/1 import MVP end to end)

- [x] Real CLI command `orpheus import <source> <destination>`: scan (source never modified), tag
      reading, Tier 0/1 identification, destination (ADR-003), copy, ledger and review queue
- [x] Domain ports `LedgerPort`, `ReviewQueuePort`, `TagReaderPort` (with `UnreadableTrackException`:
      a missing tag is normal, an unreadable file is reported)
- [x] Persistence adapter: first Flyway migration, Panache entities and repositories; first runtime
      check of SQLite through `hibernate-community-dialects` (the gate of ADR-010)
- [x] Tag reader `JaudiotaggerTagReader`, round trip checked on the 6 formats with ffmpeg-generated
      fixtures, audio/video discriminator on MP4 containers (ADR-002)
- [x] `DestinationPath` (ADR-003): artist/year-album/disc/number-title, Various Artists; isolated
      track under the artist when known; missing year as a text placeholder, never an invented year
- [x] `@TopCommand` boot problem solved: an explicit CDI scope (`@Dependent`) was needed next to the
      picocli annotation
- [x] Real run on the whole library: source never touched. The final adversarial review found two
      high-severity problems, both fixed: a destination path collision was treated as a duplicate
      without comparing content (more than 250 tracks with different audio silently discarded, now
      compared by size and SHA-256), and an `IOException` on one track aborted the whole command.
      After the fix: 2197 of 2197 valid tracks in the destination, 205 collisions disambiguated

## Done (session of 2026-07-23, second round: Tier 0/1 and Tier 2 release selection)

- [x] Tier 0/1 identification engine (SPEC.md 4.4) and local Various Artists rule (SPEC.md 4.3,
      `LocalIdentifier`): clustering by folder, then by compatible artist and album (not by title
      alone, or two same-named albums of different artists would merge into a false, irreversible
      Various Artists)
- [x] Tier 2 release selection (ADR-001): `ReleaseDate` with variable precision (no `LocalDate`
      faking a precision that does not exist), `ReleaseType`, `Release`, `ReleaseSelector`. An album
      always beats EP and single; among the rest the oldest wins, and a year-only date never beats a
      more precise date of the same year
- [x] Tier 2 ports (`FingerprintLookupPort`, `ReleaseLookupPort`), interfaces only
- [x] Adversarial review: 4 real problems fixed, 2 of them silent and permanent merges of distinct
      albums (placeholder list only in English; the Various Artists rule ignoring a consistent album
      artist tag, false positives on records with different featuring per track), plus a folder with
      tagged and untagged tracks that fragmented, and a three-way collision whose result depended on
      input order (clustering made order-independent)

## Done (session of 2026-07-23: first real domain code)

- [x] Domain model (SPEC.md section 3): `Track`, `Album`, `LedgerEntry`, `ReviewItem` and supporting
      types
- [x] Normalized Levenshtein (ADR-015) with an explicit fold for extended Latin letters that have no
      NFKD decomposition (ø, þ, ð, ł, æ, œ, ß): NFKD alone covered only the precomposed cases
- [x] Hungarian algorithm (ADR-016): the 5 edge cases of the ADR, a brute-force oracle on 120 random
      matrices, explicit refusal of costs that would overflow the internal big-M
- [x] `AlbumCompleteness.UNVERIFIED`: the original COMPLETE/PARTIAL pair could not represent a Tier 0/1
      grouping
- [x] Pre-existing bug in the root POM fixed: `<argLine>@{argLine}</argLine>` without Jacoco crashed
      any `mvn test` as soon as a module had a test class

## Done (session of 2026-07-17: Maven scaffolding)

- [x] Multi-module Maven scaffolding (8 modules, including the since removed `orpheus-web`), real
      reactor build checked, negative test of the enforcer rule that keeps `orpheus-domain` pure
- [x] Quarkus 3.33 (LTS stream), JDK 26 via sdkman and the `requireJavaVersion` rule, SQLite via
      `hibernate-community-dialects`, jaudiotagger

## Done (session of 2026-07-16: technology evaluation and technical ADRs)

- [x] ADR-0006 (Java on a current JVM, keeping the tiered strategy of the SPEC) and ADR-0007 to 0016
      (Quarkus, multi-module Maven, hexagonal architecture, SQLite/Flyway/Panache, virtual thread per
      track, Picocli, review frontend, REST Client and Jackson, own Levenshtein and Hungarian
      algorithm)
- [x] Java development infrastructure: formatter, git hooks, markdown lint, versioning from
      git-flow, enforcer, versions plugin
- [x] Python/beets code removed from the working tree (`44146f0`), still available in the git
      history as a reference

## Done (earlier sessions, Python/beets pipeline, code removed)

- [x] ADR-0001 to 0005 and `docs/SPEC.md`
- [x] Python Tier 2 and `reconstruct-orphans`: checked end to end on a scratch copy with real audio,
      never applied to the pilot library because of the fragmentation risk (Sonata Arctica -
      Ecliptica)
- [x] Tier 0/1, ledger, web UI per-track view, guided YouTube replacement, root cause of the "86%
      Non-Album" found and fixed, year from the wrong release fixed (Burn, Stormbringer, Come Taste the
      Band)
