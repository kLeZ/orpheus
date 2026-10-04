# ADR-0021: Tag writing rules for the copies

**Status**: accepted, 2026-09-27 (proposals approved by the user as a whole; source and behaviour of
the genre confirmed). All rules are decided. Amended by ADR-0024 (2026-10-04): every track placed on a
Various Artists release is a compilation track (T08, T17), with PERFORMER and COMPOSER. The rules are
written as a table because the user plans to move to a rule engine (a macro topic raised on
2026-09-27, to be addressed later): each row is an independent rule, which can be evaluated without
reading the code.

## Context

The import of 2026-09-27 organized the folders but did not write tags: the copies had the source tags
("Sugar Sugar" under `The Archies/` but tagged "Beatles", many Linkin Park files without a title).
DESIGN.md includes in the mission "ID3 tags fixed where the data exists".

## Principles

1. **Folder and tags say the same thing.** The values written into the tags are those used to compute
   the destination path; a player and the file system show the same library.
2. **Copies only.** The source is never modified (SPEC.md section 2).
3. **No data lost.** Before overwriting a field, the original value is kept in the ledger (field by
   field) and in a file tag (`ORPHEUS_ORIGINAL_<FIELD>`; until the English migration the prefix was
   `ORPHEUS_ORIGINALE_`, used only in test copies, never in a real library). Consistent with the
   user's decision on copies: Orpheus reorganizes, it does not clean up.
4. **Unknown fields untouched.** Comments, cover art, ratings, play counts, lyrics and every field not
   listed below are not touched.

## Sources

| Code | Source | Reliability |
| ---- | ------ | ----------- |
| `CONS` | Release chosen by multi-track consensus for the group (ADR-001) | highest |
| `REL` | Release of the track identified by Tier 2 (ADR-0018, ADR-0020), after release unification | high |
| `MBG` | MusicBrainz genres: release group, then artist (Genre section) | high, genre only |
| `TAG` | Existing tag, if not a placeholder and not malformed (`Placeholder`, tag reader) | medium |
| `FILE` | File name (`FileNameParser`) | low |
| `DIR` | Folder name (year, `LocalEvidence`) | low |
| `PAD` | Top-level folder as artist (`Track.artistFromPath`) | low |
| `CAN` | Canonical artist name (`CanonicalArtistNames`) | transformation, not a source |

`CONS` and `REL` are alternatives: a track has at most one release. In the table "release" means the
one the track has.

## Actions

- `WRITE`: the field takes the value of the source even if the existing tag is valid and different;
  the previous value is kept (principle 3).
- `FILL`: the field takes the value only if the existing tag is missing, a placeholder or malformed.
- `KEEP`: the field stays as it is.

For each field the rule with the lowest priority (1 = first) whose condition is true and whose source
has a value applies; the other rules of the same field do not apply.

## Rules

| Id | Field | Condition | Source | Action | Priority |
| -- | ----- | --------- | ------ | ------ | -------- |
| T01 | `ARTIST` | track with a release | artist credit of the track in the release tracklist | `WRITE` | 1 |
| T02 | `ARTIST` | no release | `TAG` | `KEEP` | 2 |
| T03 | `ARTIST` | no release, `TAG` missing | `PAD` transformed by `CAN` | `FILL` | 3 |
| T04 | `TITLE` | track with a release | title of the track in the tracklist | `WRITE` | 1 |
| T05 | `TITLE` | no release | `FILE` | `FILL` | 2 |
| T06 | `ALBUM` | track with a release | title of the release (after release unification) | `WRITE` | 1 |
| T07 | `ALBUM` | no release, track in a Tier 0/1 album | title of the album used in the path | `FILL` | 2 |
| T08 | `ALBUMARTIST` | track with a release, owned compilation (consensus on a Various Artists release) | "Various Artists" | `WRITE` | 1 |
| T09 | `ALBUMARTIST` | track with a release | artist folder of the path (release credit, or the track's artist for a single found only on a compilation) | `WRITE` | 2 |
| T10 | `ALBUMARTIST` | no release, track in an album | artist folder of the path | `FILL` | 3 |
| T11 | `DATE` | track with a release | year of the release (after release unification) | `WRITE` | 1 |
| T12 | `DATE` | no release, album with a year | year used in the path (tag, `DIR`, year from a release for tagged albums, ADR-0020 point 5) | `FILL` | 2 |
| T13 | `TRACKNUMBER` | track with a release | position in the tracklist | `WRITE` | 1 |
| T14 | `TRACKNUMBER` | no release | `FILE` | `FILL` | 2 |
| T15 | `DISCNUMBER` | track with a release, release with several discs among the placed tracks | disc in the release | `WRITE` | 1 |
| T16 | `DISCNUMBER` | otherwise | none | `KEEP` | 2 |
| T17 | `COMPILATION` | condition of T08 | `1` | `WRITE` | 1 |
| T18 | `GENRE` | see Genre section | `MBG` | `WRITE` | 1 |
| T19 | `MUSICBRAINZ_TRACKID` | track with a release | recording id | `WRITE` | 1 |
| T20 | `MUSICBRAINZ_ALBUMID` | track with a release | release id (after unification) | `WRITE` | 1 |
| T21 | `MUSICBRAINZ_ARTISTID` | track with a release | ids of the track's artists | `WRITE` | 1 |
| T22 | `MUSICBRAINZ_ALBUMARTISTID` | track with a release, not an owned compilation | ids of the release's artists | `WRITE` | 1 |
| T23 | `MUSICBRAINZ_RELEASEGROUPID` | track with a release | release group id | `WRITE` | 1 |
| T24 | `ORPHEUS_ORIGINAL_<FIELD>` | a `WRITE` changed an existing value | previous value | `WRITE` | 1 |
| T26 | "applied tag correction" review item | a `WRITE` changed an existing value | field, previous value, new value | recorded in the database | 1 |
| T25 | every other field | always | none | `KEEP` | 1 |

Cases covered explicitly:

- **Contrast with the tag** (ADR-0018 point C, "Sugar Sugar"): T01 writes the identified artist, T24
  keeps "Beatles".
- **Values from the file or folder name**: only `FILL` (T03, T05, T07, T10, T12, T14): they never
  replace an existing tag.
- **Unidentified tracks with valid tags**: no field changes, except the empty fields the local
  evidence can fill.

## Genre

Source: MusicBrainz genres (confirmed by the user on 2026-09-27), already integrated: no new key, same
rate limit, same cache. Checked on 2026-09-27: on the recording the genres are almost always empty; on
the release group they are there with the community's votes ("Cross Road": hard rock 11, rock 10, glam
metal 2...), and on the artist ("Bon Jovi": rock 17, hard rock 16, glam metal 13...). Two more lookups
are needed (release group and artist), cached: about 500 release groups and 180 artists on the
library at the time. The Spotify APIs are to be evaluated later (the user's decision).

| Id | Condition | Source | Action | Priority |
| -- | --------- | ------ | ------ | -------- |
| G01 | track with a release, release group with genres | genres of the release group by decreasing votes | `WRITE` | 1 |
| G01b | track without a release in an album folder where another track has a release, release group with genres | genres of that release group | `WRITE` | 1 |
| G02 | artist known to MusicBrainz (id from an identified track of the same canonical artist), artist with genres | genres of the artist by decreasing votes | `WRITE` | 2 |
| G03 | no MusicBrainz genre | valid `TAG` | `KEEP` | 3 |

- Action `WRITE` (the user's decision: the genre is made uniform even if the file already has one).
  The previous genre is kept by T24 and the change is recorded in the database as an "applied tag
  correction" review item, so that the CLI and the web interface can offer to go back: the user may
  want to manage some tags by hand, and the software must offer a way out.
- Spelling: capitalized initials ("hard rock" becomes "Hard Rock").
- Same genre for all tracks of an album: G01 works at release group level; the tracks of a Tier 0/1
  album without a release take the release group of an identified track in the same album folder
  (G01b, added on 2026-09-27 during the unsupervised work, to be confirmed), otherwise G02 from the
  album's artist.
- Placeholder genres treated as missing: "Other", "Altro genere", "Genre", "Unknown", ID3v1 numbers in
  parentheses without a name.
- Artists never identified by Tier 2 (no MusicBrainz id): no genre. A name search on MusicBrainz is
  possible but ambiguous; excluded from this first version.
- **Number of genres: up to five, in standard form** (the user's decision of 2026-09-27, who uses
  up-to-date players on Linux and Android and needs no compatibility with old software): the genres of
  the release group, or of the artist, with the most votes, at most five. MP3: a single ID3v2.4 `TCON`
  frame with the values separated by the null byte. FLAC and OGG: one `GENRE` field per genre. No text
  separator (";", ",", "/").
- **Order of the values, for Android.** From the AOSP code (not tried on a device): the ID3 parser
  passes the frame as a C string (`AMediaFormat_setString(meta, key, s.c_str())` in
  `MP3Extractor.cpp`), so Android sees only the **first** genre of an MP3; the Vorbis comment parser
  (`parseVorbisComment` in `MetaDataUtils.cpp`) overwrites the key at every `GENRE` field, so Android
  sees only the **last** genre of a FLAC or OGG. The media database then stores a single genre per file
  (`MediaColumns.GENRE` in `ModernMediaScanner.java`), and that is where Apollo reads it from (the
  maintained fork by nuclearfog: it uses `MediaStore.Audio.Genres`). For Android to show the most voted
  genre in every format: in MP3s the genres go in decreasing order of votes, in FLAC and OGG in
  increasing order (the most voted last). For MPD the order does not matter.
- Data collected on 2026-09-27 for the decision:
  - standard form: ID3v2.4 allows several values in the same frame separated by the null byte
    ([id3.org specification](https://id3.org/id3v2.4.0-frames): "All text information frames supports
    multiple strings, stored as a null separated list"); in Vorbis comments the field is repeated;
    ID3v2.3 has no standard form (in practice "/");
  - MPD 0.24.15, tried on a v2.4 MP3 and a FLAC with three genres: three distinct genres, the file shows
    up when searching for each ([protocol documentation](https://mpd.readthedocs.io/en/latest/protocol.html));
  - Amarok 3.3.3, tried with its collection scanner: it reads all values but joins them in a single
    string "Hard Rock / Rock / Glam Metal", so in the genre browser every combination becomes a genre of
    its own ([bug 119539](https://bugs.kde.org/show_bug.cgi?id=119539), open since 2006);
  - Clementine: not tried; same reading library (TagLib) as Amarok
    ([issue 637](https://github.com/clementine-player/clementine/issues/637));
  - Android and Apollo: see the point above.

## Formats

- MP3: ID3v2.4, UTF-8. An ID3v2.3 tag is converted to ID3v2.4; a file with only ID3v1 receives an
  ID3v2.4 initialized from the ID3v1 tag. The ID3v1 tag, if there is one, stays as it was (players read
  it only when there is no ID3v2). MusicBrainz identifiers as Picard does: `UFID`
  `http://musicbrainz.org` for the recording, `TXXX` "MusicBrainz Album Id" and the like for the
  others. `ORPHEUS_ORIGINAL_<FIELD>` is a `TXXX` with that description, several values separated by the
  null byte.
- FLAC, OGG: Vorbis comments, field names as in the table (Picard convention); one repeated field per
  value, also for `ORPHEUS_ORIGINAL_<FIELD>`.
- WMA: ASF attributes (`WM/Genre` repeated, `MusicBrainz/Track Id` and the like as Picard does); the
  originals in `ORPHEUS_ORIGINAL_<FIELD>` attributes.
- MP3, tag boundary: the ID3v2 tag is written with `ID3v24Tag.write` up to the end of the tag declared
  in the header, not with `commit()`. jaudiotagger with `commit()` writes up to the first MPEG frame it
  recognizes and treats everything before it as padding: in the first real import of 2026-09-27 it
  overwrote from 58 bytes to 49 KB on 19 copies (MPEG-2 frames at the start of "Beatles/ - Sugar
  Sugar.mp3", about half a second of audio; the source was never touched). After writing, the bytes
  outside the tags are compared with those from before; if they differ, the copy goes back to how it
  was and the error is counted.
- M4A/MP4 (none in the source at the time): a single genre, the most voted; no
  `ORPHEUS_ORIGINAL_<FIELD>` field, the original stays only in the database.

Check of 2026-09-27 on copies of real source files (ID3v2.3 MP3 with cover art and ID3v1, ID3v2.4 MP3
with cover art and `FMPS_*` fields, MP3 with `TRCK` "3/12", MP3 with only ID3v1, FLAC with cover art,
OGG, WMA), comparing `mutagen-inspect` before and after and the md5 of the audio decoded by ffmpeg:

- decoded audio identical in all files;
- no untouched field lost: cover art, comments, `FMPS_*`, composer, track total ("7/12" after writing
  track 7); in the WMA only attributes that were empty disappear;
- MP3: a single `TCON` frame with three values separated by the null byte, UTF-8 encoding; FLAC and
  OGG: three `GENRE` fields, the most voted last;
- Amarok's scanner reads all values, and because of the choice made for Android it shows the reverse
  order in FLAC and OGG ("Glam Metal / Rock / Hard Rock" against "Hard Rock / Rock / Glam Metal" in the
  MP3s): in Amarok's genre browser an album owned in two formats appears under two different strings,
  which are separate genres anyway because of bug 119539.

## Revision after the first import (the user's decisions of 2026-09-27)

Status: accepted and implemented on 2026-09-27.

- **Tags and folder tree always consistent.** Every value that appears in the destination path is
  written into the tag even when the file already has a valid one, with the original kept (T24) and a
  review item (T26). Reason: an informative folder tree makes sense only if it says the same thing as
  the tags. In the first import 24 copies had a year different from the folder and 4 a different
  artist. In practice:
  - T07, T10, T12 go from `FILL` to `WRITE` (album, album artist, year of an album without a release);
  - T02: the tag's artist takes the canonical form used for the folder ("ACDC" becomes "AC/DC",
    "Beatles" under `The Beatles/` becomes "The Beatles");
  - the disc number derived from the folder ("CD2") fills the tag if missing, because it serves the
    "Disc N" subfolder.
- **ID3v1 updated.** If the file has an ID3v1 tag, it receives the same values as ID3v2 within the
  limits of the format: 30 characters per field; genre, the first of the genres present in the ID3v1
  list, otherwise undefined; track number only in ID3v1.1 (a v1.0 becomes v1.1 only if the comment
  fits in the 28 characters of v1.1, otherwise it stays without a number). The comment is not touched;
  an empty field in ID3v2 leaves the ID3v1 value. It also applies to files whose ID3v2 does not change.
  In the first import 333 MP3s out of 2125 had a mismatched ID3v1. Characters outside ISO-8859-1 become
  "?" (jaudiotagger would truncate them to one byte). Measured side effect: in 8 MP3s the last frame is
  truncated and ffmpeg decodes the ID3v1 bytes in its place, so the last 3-13 ms decoded change with
  the tag; the audio bytes stay identical.
- **Genres for albums never identified (G01c, between G01b and G02).** For an album without a release
  whose folder is already ordered and consistently tagged (same album and same album artist, or
  without an album artist the same artist, on all tracks; not Various Artists), a MusicBrainz search by
  artist and album title. Only release groups with identical title and credit count, apart from case,
  spaces and typographic characters. A single one, or a single album without secondary types among
  them (Deep Purple "Burn": two EPs and an album with the same name): genres of that release group.
  Several release groups by the same artist: the artist's genres. Otherwise no genre. It overrides the
  exclusion of the name search written above, limited to this case. The artist spelling of the chosen
  release group counts as the canonical name (ADR-0020, point 3), because it matches the tag's apart
  from case: "Pain of Salvation" instead of the most frequent local variant "Pain Of Salvation", which
  without this rule would also have ended up in the tags. Likewise the title of the release group
  replaces that of the tracks in the folder and in the tags (the user's decision of 2026-09-28): "Enema
  of the State", not the spelling "Enema Of The State" of one of the tracks. Only if the two titles
  differ in case, spaces or typographic characters: a local title with something more ("Entropia
  (Japanese Edition)" next to "Entropia") is another edition and stays separate (the user's decision of
  2026-09-28). Widened on 2026-10-04 (ADR-0018, rule 9): "&"/"and", a leading article and punctuation
  are ignored too.
- **ASCII characters only where an equivalent exists.** Typographic dashes, apostrophes, quotes,
  ellipses and spaces become ASCII in the written tags, in the file texts the rules would not touch
  (artist, title, album, album artist, genre) and in folder names: full-text search does not match
  "blink‐182" (U+2010, the MusicBrainz form) with "blink-182". Accented letters stay.

## Prerequisite

The provenance of every value in `TrackMetadata` (item in `docs/TODO.md`): the conditions "valid
`TAG`" and "value from `FILE`/`DIR`" need to know where each field comes from, which is lost in
`LocalEvidence`.

## Consequences

- The tags become consistent with the folders; the original values stay recoverable from the ledger
  and from the file itself.
- The title and track number of an identified file come from MusicBrainz even when the tag had a
  different but equivalent spelling ("In The End" becomes "In the End").
- A Tier 2 identification error now also propagates to the tags, not just to the folder: the
  `TIER2_RECONSTRUCTION` review item and the original values kept serve to undo it.
