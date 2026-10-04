# ADR-006: Technology stack: migration from Python/beets to Java/JVM

**Status**: accepted, 2026-07-16.

## Context

`docs/ROADMAP.md` listed "technology evaluations" as the top priority ("Now"), motivated by real bugs
found in the beets autotagging layer while implementing Tier 2: a shared, rate-limited AcoustID key
hardcoded in the `chroma` plugin, `mb_trackid` unreliable in quiet mode, configuration not loaded in
a CLI command. `docs/SPEC.md` was written on purpose independently of the stack, to make this
evaluation possible without rewriting it in case of a change. The user also prefers, for their own
reasons, pure OOP and strong static typing (Java) to Python.

The analysis below was not done from memory: the Orpheus source code (3983 lines, 27 files) was read,
and so was the source code of beets itself inside `.venv` (`beets/autotag/distance.py`, `match.py`,
`beetsplug/musicbrainz.py`), to understand precisely how much of Orpheus's logic really depends on
beets, and how much of the beets "matching engine" is its own work rather than a thin wrapper on
MusicBrainz.

## Decision

Rewrite in Java, on an up-to-date JVM (JDK 26/27 train), **keeping the tiered strategy already
defined in SPEC.md** (Tier 0/1 text-first through MusicBrainz search, Tier 2 fingerprint for the
residue and isolated tracks, Tier 3 fallback), not a switch to fingerprint-first as the primary way.

Reasons:

- Only 8.5% of the Orpheus code at the time (337 lines: `path_helpers.py`, `acoustid_key_fix.py`,
  `import_tracker.py`, `reconstruct_plugin.py`) is really bound to beets, because they are native
  plugins running inside its Python process. The rest (about 91.5%) does not import it at all, or
  only reads its SQLite output (portable through JDBC without depending on beets code).
- The beets matching engine (`distance.py` + `match.py`, about 1080 lines: a weighted multi-field
  distance with normalized Levenshtein, plus an optimal file-to-track assignment via the
  Jonker-Volgenant algorithm on a cost matrix) is real algorithmic work, not a thin wrapper on
  MusicBrainz relevance ranking, but its value is concentrated on the generic case (Tier 0/1). On the
  "chaos" case that is the reason Orpheus exists, that engine does not just fail to help: with
  placeholder tags the string distance between empty and full saturates at the maximum on every text
  field, actively worsening the confidence in the match. It is no accident that it is already
  bypassed: `reconstruct.py` (Tier 2) is verified standalone, direct fingerprint via
  `acoustid.match` + `musicbrainzngs`, no beets `Item`/`Album` object involved. The rules of ADR-001
  (album always beats EP/single, a hard hierarchy that cannot be expressed as a weighted average) and
  ADR-004 (priority through the Shannon formula) already are, and remain, Orpheus's own code,
  independent of the stack.
- Porting the beets matching engine was judged feasible with acceptable effort: Levenshtein and the
  optimal assignment algorithm (LAP) are well-known, well-defined algorithms, not "tribal knowledge"
  accumulated over years of edge cases, unlike the integration bugs already found (shared key, timing
  of import events), which are exactly the kind of debt the rewrite wants to remove.
- Java over Kotlin: sealed classes and exhaustive pattern matching on switch have been stable in Java
  since JDK 21 (no remaining advantage for Kotlin there). The only remaining Kotlin advantage,
  compiler-enforced null safety, can be mitigated with JSpecify (annotations plus NullAway, IntelliJ
  or Error Prone). The stated goal of using emerging JVM features (Valhalla value classes, preview
  estimated not before JDK 28; in perspective the Vector API, final probably not before JDK 30/31)
  makes Java preferable: being the reference language these JEPs are designed on, it gets support for
  new features before the Kotlin compiler. Compact object headers (Lilliput) and AOT caching (Leyden)
  are already real since JDK 25/26 and apply equally to both languages, so they do not weigh on the
  choice.

## Consequences

Scope of the port: a typed reimplementation of `distance.py`/`match.py`; typed HTTP clients for
MusicBrainz and AcoustID (`fpcalc` stays an external native binary invoked as a process, unchanged,
in any stack); `jaudiotagger` for tag I/O on the six formats (coverage confirmed for
MP3/FLAC/OGG/WAV/WMA/M4A, but WMA/M4A must be checked again in practice on the user's real files,
with the same rigour as ADR-002 rather than trusting the documentation alone); `TarsosDSP` for the
spectral heuristics of `quality.py`/`eq_heuristic.py`, with numerical revalidation on the real cases
already used as a test bench (windowing and normalization differences between libraries can shift
the results). All the domain logic already independent of beets must be rewritten one to one in the
new language: not free, but with no design unknowns, only translation ones. The web layer (FastAPI at
the time, 687 lines) is not an open problem, since the Java ecosystem offers mature solutions, both
light and complete, for an interface of a few pages: it is only an implementation choice to make
separately, with no impact on the stack decision.

Before starting the actual port a gate is needed that component-level spikes alone (jaudiotagger on
WMA/M4A, TarsosDSP on the known spectral cases, a distance/LAP prototype) do not cover: an end-to-end
regression oracle. Orpheus has zero automated tests; the working Python version must therefore be run
on a curated set of known real cases (Deep Purple "Burn", Machine Head 40th Anniversary, Sonata
Arctica "Ecliptica", Skunk Anansie "Because of you", Bananas, Concerto), recording the decisions
taken (final paths, ledger entries, generated review items) as a baseline the Java version must
reproduce. Some current Python behaviours are themselves the bug that motivated the rewrite (shared
AcoustID key, unreliable `mb_trackid`), so the baseline on the delicate cases needs an expected output
validated by hand, not a blind diff against the Python output.
