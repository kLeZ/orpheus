# Orpheus

Orpheus takes a folder of audio files of mixed origin (rips, old downloads, partial albums, files with
missing or broken tags) and copies it into an organized music library: an `Artist/Year - Album/NN -
Title` tree, tags corrected where the data exists, isolated tracks placed back on their original
album through audio fingerprints when the tags are not enough. The source folder is never modified.

It is built for the messy case. Partial album folders, missing tags and loose tracks are its normal
input, not an exception; a library that is already well tagged is better served by
[MusicBrainz Picard](https://picard.musicbrainz.org/). The reasoning behind this, and the criteria by
which Orpheus fails, are in [`docs/DESIGN.md`](docs/DESIGN.md); read it before contributing.

## Status

The import command works and has been checked on a real library of about 2200 files: it groups,
identifies, copies, rewrites the tags of the copies and reports damaged files. There is no user
interface yet: the review queue (the cases Orpheus could not settle, and every change it made) is
only in the SQLite database. A web interface is designed in
[ADR-0025](docs/adr/0025-web-interface.md). Next steps are in [`docs/ROADMAP.md`](docs/ROADMAP.md).
Built with Java 26, Quarkus and multi-module Maven
([ADR-0006](docs/adr/0006-java-jvm-stack.md)).

## How it works

Identification goes through increasing tiers, the cheapest first (details in
[`docs/SPEC.md`](docs/SPEC.md)):

- Tier 0/1: local evidence only. Tracks are grouped by folder and by consistent tags; titles,
  numbers and years missing from the tags are taken from file and folder names.
- Tier 2: audio fingerprints. `fpcalc` computes a Chromaprint fingerprint,
  [AcoustID](https://acoustid.org/) returns the candidate MusicBrainz recordings, a filter discards the
  unrelated ones linked to the same fingerprint
  ([ADR-0018](docs/adr/0018-tier2-candidate-recording-filter.md)), and
  [MusicBrainz](https://musicbrainz.org/) gives the releases to choose from
  ([ADR-0001](docs/adr/0001-release-selection-and-multi-track-consensus.md)).
- Tier 3: a heuristic on the file name, only as a suggestion in the review queue.

Every file of the source ends up in the destination: identical copies get a `(2)` suffix, different
audio on the same path a `(version 2)` suffix
([ADR-0019](docs/adr/0019-copies-editions-formats.md)). Every action is recorded in a ledger, every
tag value replaced is kept in the file as `ORPHEUS_ORIGINAL_<FIELD>`
([ADR-0021](docs/adr/0021-tag-writing-rules.md)), and every file is decoded once to report damage
such as truncation or dropouts ([ADR-0023](docs/adr/0023-damaged-audio-files.md)).

## Requirements

- Java 26 (the Maven wrapper is included; `.sdkmanrc` selects the JDK with
  [SDKMAN!](https://sdkman.io/)).
- `ffmpeg` and `fpcalc` (Chromaprint) on the `PATH`.
- An [AcoustID API key](https://acoustid.org/new-application) for the fingerprint identification;
  without it Orpheus works with local evidence only.
- [rumdl](https://github.com/rvben/rumdl), only for the Markdown check of the git hooks.

## Usage

```sh
./mvnw clean install
java -Dorpheus.data-dir=DATA -Dorpheus.cache-dir=CACHE \
     -jar orpheus-app/target/quarkus-app/quarkus-run.jar [--dry-run] SOURCE DESTINATION
```

- `ACOUSTID_API_KEY` in the environment (or in a `.env` file, see `.env.example`) enables the
  fingerprint identification.
- `--musicbrainz-user-agent "Orpheus/0.1 (you@example.org)"` sets the contact MusicBrainz asks for in
  its usage policy.
- `--dry-run` computes destinations, ledger and review queue without copying or writing anything.
- `orpheus.data-dir` is where the SQLite database (ledger and review queue) goes; `orpheus.cache-dir`
  is an optional cache of the AcoustID and MusicBrainz answers, useful to repeat a run without waiting
  for the rate limits again. Destination, data folder and cache cannot be inside the source.
- The names used when a piece of information is missing ("Unknown Artist", "Unknown Year", "Unknown
  Track", "Unidentified", "Disc 2", "(version 2)") can be changed with the `orpheus.placeholders.*`
  properties (listed in `orpheus-app/src/main/resources/application.properties`).
- **The destination must be empty.** The copies have rewritten tags and are no longer identical to the
  source, so a second import into the same destination, or resuming an interrupted one, would put a
  `(2)` copy next to every file. To repeat an import, empty the destination and the data folder.
  Incremental import is planned.

## Project constraint

No component of this software authenticates to Spotify to read or save audio streams (no
`libspotify`/`librespot`, public Web API only). See `docs/DESIGN.md`.

## License

[GNU Affero General Public License v3.0 or later](LICENSE).
