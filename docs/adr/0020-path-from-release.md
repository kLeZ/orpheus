# ADR-0020: Destination path from the release for identified tracks

**Status**: accepted, 2026-09-27. Implements corollary 2 of DESIGN.md (confirmed by the user on
2026-09-26: an identified track goes to its original album) and phase 2 of the corrective plan. Point
4 is superseded by ADR-0024 (2026-10-04): compilations always go to "Various Artists".

## Context

On the real run of 2026-09-26, 278 tracks ended up in "fake albums" like `ACDC/1980 - ACDC`: an artist
folder with singles from different records was promoted to a Tier 0 album, then Tier 2 identified each
track individually and the year of its release was written into the path of the whole group. Another
41 isolated tracks had a MusicBrainz release but ended up in `Non Identificato/`, because the release
did not carry a title.

## Decision

1. **Path from the release.** A track identified by Tier 2 (individually or forced by consensus) takes
   its path from its own release: `artist credit / year - release title / [Disc N] / NN - title in the
   tracklist`. MusicBrainz provides title, credit, disc, position and title of the track in the same
   call already used (`inc=media`). The disc folder is created only if the tracks placed in that
   release are on different discs (MusicBrainz does not say how many discs the release has, only which
   one the track is on), as for Tier 0/1 albums.
2. **Artist folder.** A folder without an album tag whose name matches the artist of its tracks (or an
   artist present in the credit of each) is not an album: the tracks stay isolated and Tier 2 places
   them one by one.
3. **Canonical names.** The artist folder name uses the MusicBrainz form when an identified track of
   the same artist provides it ("ACDC" → "AC/DC"), otherwise the most frequent local variant among
   those differing only in case, spaces and punctuation ("Pain of Salvation"/"Pain Of Salvation",
   "Greenday"/"Green Day"). Typographic dashes and apostrophes become ASCII in folder names.
4. **Singles found only on compilations.** A single whose recording appears on MusicBrainz only on
   Various Artists compilations stays under its artist, with the compilation as the album. Only a group
   of tracks forced by consensus onto that compilation, that is a record the user owns, goes under
   "Various Artists" (ADR-003). Superseded by ADR-0024.
5. **Year of tagged albums.** A Tier 1 album with a real album tag but no year takes the year from a
   Tier 2 release whose title matches the album tag. The tracks are not moved: a "Greatest Hits" owned
   as a record stays whole.
6. **Editions of the same album.** Tracks placed on different releases of the same album (same credit,
   same title) go into the folder of the release with the most tracks, the oldest on a tie; the disc of
   the other release is lost. A Tier 1 album with the same artist, the same title and at least one song
   in common takes the year of that release ("Ecliptica" in a single folder instead of three).
7. **Track title.** With a known title, the recordings and releases where the track carries that title
   win (the same recording appears on demo compilations with working titles).
8. **Contrast with the tag** (ADR-0018 point C): with the path from the release, the file goes under
   the identified artist.

## Consequences

- The unidentified tracks of a Tier 0 group keep a single group path and receive an `UNIDENTIFIED`
  review item.
- The title and number in the file name come from the MusicBrainz tracklist, not from the tag: more
  consistency within the album, at the cost of overwriting the source spelling in the file name. Since
  ADR-0021 the tags of the copy take the same values too, with the original preserved.
- A wrong canonical name in MusicBrainz propagates to all the artist's tracks.
