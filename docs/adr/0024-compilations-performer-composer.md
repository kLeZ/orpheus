# ADR-0024: Compilations in Various Artists, performer and composer

**Status**: accepted, 2026-10-04 (the user's decisions in the review of the rules on the same day).
Amends ADR-0020 (folder of the tracks placed on a compilation) and ADR-0021 (written fields).

## Context

Until ADR-0021 a track went into `Various Artists/` only if multi-track consensus had forced several
source tracks onto the same compilation; a loose track found only on a compilation stayed under its
own artist, and the tags said neither who performs the song nor who wrote it. The user: "I'd rather
compilations sit in Various Artists, all gathered in their album, with album artist, performer and
composer in the metadata, consistent with the compilation". Example: "Re-Machined", a tribute for the
40 years of Deep Purple's "Machine Head", with "When a Blind Man Cries" performed by Metallica.

## Decision

1. **Folder.** Every track placed on a release credited to Various Artists goes to
   `Various Artists/<year> - <album>/NN - <performer> - <title>`, even if loose. The performer in the
   file name is the one in the tracklist, the same as the ARTIST tag.
2. **Compilations scattered by performer.** In the source "Re-Machined" sits in nine folders, one per
   performer, with one track each ("Metallica/Re-Machined.../09 - When A Blind Man Cries.mp3"). If the
   same album title appears under at least two artists and MusicBrainz has a Various Artists release
   group with exactly that title, those albums become a single compilation in `Various Artists/`. The
   shared title alone is not enough: two albums with the same name by different artists exist, and
   merging them would be a false Various Artists (review of 2026-07-23).
3. **Tags of a compilation track.** ARTIST and PERFORMER: who performs the song, from the credit of the
   track in the release. ALBUMARTIST: the release credit on MusicBrainz. COMPILATION: 1.
4. **COMPOSER on every track** whose MusicBrainz recording is known: the authors from the "composer"
   and "writer" relationships of the performed work, full names in MusicBrainz order, one value per
   author ("Sliver": Chad Channing, Kurt Cobain, Krist Novoselic). For tracks grouped from tags, which
   have no recording, it is derived from the fingerprint without changing their placement, only if the
   fingerprint agrees with the tag's artist. Without known authors the file's field stays; a different
   composer in the file is kept in `ORPHEUS_ORIGINAL_COMPOSER` (T24).
5. **Field format.** COMPOSER: TCOM in ID3v2.4, COMPOSER in Vorbis comments, several values in the same
   field as for genres. PERFORMER: the PERFORMER field in Vorbis comments; in ID3v2.4 the TMCL frame
   (Musician Credits List) with the role "performer". jaudiotagger 3.0.1 writes `PERFORMER_NAME`, which
   players do not read.

## Alternatives considered

- **Only owned compilations** (several tracks of the same compilation in the source): discarded by the
  user.
- **Composer only for compilations**: discarded by the user.
- **Composer in alphabetical order or surnames only**: discarded by the user in favour of full names in
  MusicBrainz order.
- **Performer's folder for loose tracks from compilations** (tried in the autonomous loop of
  2026-10-04): discarded by the user.

## Consequences

- One MusicBrainz call per recording for the authors (`composers-v1` cache) and an AcoustID lookup for
  every track grouped from tags: the first import after this decision takes much longer (51 minutes
  instead of about 8 on the 2204 files), the following ones read from the cache.
- Reading TMCL as performer in MPD and Amarok has not been verified.
- The composer is only as good as MusicBrainz: "When a Blind Man Cries" has only Ritchie Blackmore as
  author of the work there.
