# ADR-0018: Filtering candidate recordings and releases in Tier 2

**Status**: accepted, 2026-09-26. Amends ADR-001 (the "Consequences" section, on the release type).

## Context

The full real run of 2026-09-26 on `~/Musica` showed that Tier 2 often assigns releases of artists
other than the track's. Measured on the 329 `RECONSTRUCTED` tracks of the ledger (fpcalc + AcoustID
lookup with `meta=recordings` + artist of the assigned release, from MusicBrainz): of the 316 tracks
with an artist tag, **111 (35%) received a release by another artist**. Of these, 71 are unrelated
artists (Bon Jovi "Bed of Roses" → Andy Williams 1963, Foo Fighters "Big Me" → Queen "News of the
World" 1977) and 40 are compilations (Nickelback "How You Remind Me" → "Monitor This", Various
Artists, 2000).

Two distinct causes, both verified on the code and on the data:

1. **Unrelated recordings inside the same AcoustID cluster.** A fingerprint returns several
   MusicBrainz recordings linked by the community, and the cluster is often contaminated *at score
   1.0*: "Bed of Roses" has 10 Bon Jovi recordings plus Andy Williams (167 s against the file's
   393 s) and a Canon by the English Chamber Orchestra (391 s). `Tier2Identifier` merged the releases
   of all recordings without telling them apart, and `ReleaseSelector` ("the oldest wins") picked
   precisely the oldest release of the unrelated recording. The AcoustID adapter did not read the
   title, artists or duration of the recordings: the domain had no way to discard them.
2. **Compilations beating the original album.** ADR-001 knowingly chose not to consider the
   secondary type of the release (Compilation, Live, Soundtrack...): a Various Artists compilation of
   primary type Album, older than the original album, wins.

No single criterion is enough, measured on the same data: duration does not exclude the Canon (391 s
against 393 s); the artist does not exclude another song by the same artist ("Lonely as You" for
"Monkey Wrench", 277 s against 231 s); the majority in the cluster picks Carl Douglas for a file
tagged Foo Fighters ("Kung Fu Fighting": 27 recordings out of 37 by Carl Douglas, only one by the Foo
Fighters).

## Decision

**A. Filtering candidate recordings, in the domain, before collecting the releases.** The
`FingerprintLookupPort` returns for each candidate the recording id, title, credited artists and
duration of the recording, plus the duration of the file computed by fpcalc (`Track` has no
duration). The filter applies, in order:

1. **Known artist = hard constraint.** If the track has a non-placeholder artist, only the recordings
   with at least one similar credited artist survive, after normalization (case, diacritics,
   punctuation, Unicode dash variants, `&`/`and`, a leading "The" or trailing ", The") and
   `NormalizedLevenshtein` with a 0.85 threshold. Among the consistent recordings, if a normalized
   title has a strict majority only those remain; on a tie all remain. Added after checking the
   sample: the cluster of "It's My Life" also contains "All About Lovin' You", "Come Back" and "Hush
   (demo)" by Bon Jovi with a compatible duration, and the release chosen was the album "Bon Jovi" of
   1984. Offline measurement: in 122 of the 266 identified tracks the consistent recordings contain
   different titles with a strict majority, in 9 without.
2. **Duration.** A recording with a known duration differing from the file's by more than 15 s is
   discarded; unknown duration = not discarded.
3. **Dominant identity, with no known artist or against a tag the cluster does not contain.** The
   recordings are grouped by normalized title+artist. With no known artist the identity with at least
   half of the candidates survives; against a known tag a strict majority is needed, and only
   recordings with known artists count (on the real run, 6 one-to-one ties would have replaced the
   tag's artist based only on the order of the AcoustID results).
4. **Never fall back on another artist.** If no recording survives, the track has no Tier 2
   identification (see C).

Releases are collected only from the surviving recordings. Keeping the recording that produced the
winning release (an open item on `musicBrainzRecordingId`) becomes simple but stays outside this ADR.

**B. Release selection: compilations lose against the artist's releases.** The MusicBrainz adapter
reads the secondary types of the release group and the artist of the release. Before the
Album-over-EP/Single filter of ADR-001, releases with secondary type Compilation or with artist
"Various Artists" or different from the recording's artist are excluded, unless nothing else
remains.

**C. Contradiction between tag and fingerprint** (option 2 of the question below, chosen by the user
on 2026-09-26). When the cluster contains no recording by the tag's artist: if there is a dominant
identity (rule 3 of A) it is applied anyway, and the Tier 2 reconstruction review item states the
contrast with the tag explicitly; if the cluster is scattered, no identification. **Limit at the
time**: only the release and year of the identification were applied; the destination folder stayed
the tag artist's (`Beatles/1969 - Beatles/Sugar Sugar.mp3` with the release by The Archies), because
for grouped tracks the path came from the Tier 0/1 album. Completing the move touched the "artist
folder treated as an album" damage (see TODO).

## Measurement of filter A (offline prototype on the same data)

A Python prototype replicating the implemented rules (15 s tolerance, 0.85 artist threshold, majority
title, strict majority against the tag, same placeholder list), on the 329 tracks:

- 297 with a usable artist in the tag: 266 identified with recordings consistent with the tag; 17
  identified against the tag (declared contrast, point C); 14 without identification (scattered
  cluster or tie).
- 32 without a usable artist (13 without a tag, 19 with a placeholder): 31 identified with the
  dominant identity, 1 not.
- Identifications consistent with the tag lost compared with before: 0 with a 15 s tolerance (5 with
  5 s, 1 with 10 s; beyond 15 s no measurable change).

Of the 111 releases by unrelated artists or compilations in the baseline, 61 become a recording
consistent with the tag; the others fall into the contrasts, the clusters without identification or
the compilations that remain the only release available.

The effect of B was not measured on the run: it would need the MusicBrainz lookup of all releases of
the surviving recordings (about 1500 calls at 1/s). To be checked with the real run after the
implementation.

## Question: tag versus fingerprint (resolved, option 2)

The 31 contradictions are not homogeneous. Some are almost certainly **files mislabelled at origin**
(the typical chaos of downloaded files): `Beatles/ - Sugar Sugar.mp3` whose cluster contains only The
Archies, `Blind Guardian/ - Paradise Lost Gothic.mp3` → Paradise Lost, `Evanescence/ - Seether Broken
feat Amy Lee.mp3` → Seether. Here the fingerprint is right and the tag is not. Others are **covers**
with a cluster scattered over many performers (`Punk Cover/ - There She Goes.mp3` → Diana Ross & The
Supremes, Metro Station, Sixpence None the Richer, The La's), where no artist of the cluster is the
right one. Later correction: `Ray Charles/ - When A Man Loves a Woman.mp3`, cited here in a first
draft as a scattered cover, has a cluster of about 70 Percy Sledge recordings out of 76; it is a
mislabelled file, not a cover.

Options:

1. **Trust the tag**: no identification, a review item with the dominant identity. Conservative,
   never writes an artist that does not come from the source; leaves the mislabelled files to be
   fixed by hand.
2. **Trust the fingerprint when the cluster agrees**: if a dominant identity exists (at least half of
   the candidates, that is a cluster that is not scattered), it is applied even against the tag, with
   an informative review item; if the cluster is scattered (covers), no identification. Fixes the
   mislabelled files (Sugar Sugar → The Archies), but moves files out of the artist folder where the
   user had put them.

## Check on the real sample

Real import (real AcoustID and MusicBrainz) on the Bon Jovi, Foo Fighters, Nickelback, Beatles, Ray
Charles and Evanescence folders (72 audio files), compared track by track with the full run of
2026-09-26. Of the 35 tracks with a Tier 2 identification before or after:

- all releases by unrelated artists disappeared (Andy Williams, Heritage Singers, AC/DC, Queen, Kay
  Starr, Carl Douglas), and so did the Various Artists compilations where a release by the artist
  existed (Monitor This → Silver Side Up, Promo Only → Fallen);
- 4 contrasts with the tag applied and declared: Sugar Sugar → The Archies, two When A Man Loves a
  Woman → Percy Sledge, You Are So Beautiful → Joe Cocker;
- 2 tracks without identification where before there was an unrelated artist (Kung Fu Fighting,
  Seether Broken).

Left outside this ADR: releases with secondary type Demo, Live or Soundtrack win when they are the
oldest remaining ("Big Me" → "Foo Fighters Rough Mixes", demo 1994; "I Got A Woman" → the soundtrack
of "Ray", 2004). Soundtrack cannot be excluded wholesale: the Beatles' "Help!" is classified
Soundtrack on MusicBrainz and is the right release for "Yesterday".

## Later decisions (2026-09-26)

- Releases with secondary type Demo or Live, or with status Bootleg, lose like compilations (same
  rule as point B: unless nothing else remains). Soundtrack excluded from the rule ("Help!").
- Moving the file into the identified artist's folder, in contrasts with the tag, is done together
  with the fix for the "artist folder treated as an album" damage, not as a special case.

## Rules of 2026-10-04

Introduced in the autonomous correction loop the user asked for ("get to a perfect library"), checked
with full imports and then approved by the user one by one the same day. The 46 tracks in
`Unidentified` of the 2026-10-04 import often had clear AcoustID matches that the filter discarded.

1. **Artist from the folder.** Without an artist tag, the top-level folder selects the consistent
   recordings ("Skunk Anansie/ - Because of you.mp3"), without declaring contrasts.
2. **Majority by artist.** With a known title only the recordings with that title count, grouped by
   artist; a credit that extends the other at a word boundary is the same artist ("Bill Haley" and
   "Bill Haley & His Comets", "George Thorogood & the Destroyers"; "Queen" and "Queens of the Stone
   Age" not).
3. **Declared versions.** Among recordings, and releases, with a similar title, those with a version
   qualifier (acoustic, live, demo, remix, mix, instrumental, edit, version, unplugged, karaoke,
   remaster, remastered, radio) absent from the local title lose. Bon Jovi "Always" goes to the 1994
   single instead of "Always (live in Montreal)" on "These Days".
4. **Title without parentheses.** The local title is also compared without its parenthesized parts:
   "Surfing USA (Punk Cover)" and "Surfin' USA".
5. **Reserve identity.** If the recordings of the tag's artist lead to no release (only undated
   bootlegs or no release at all), the dominant identity of the rest of the cluster applies, with its
   contrast: "Holy Diver" → Dio, "Kung Fu Fighting" → Carl Douglas.
6. **Single-track albums.** Without a year and without releases confirming its title, it is placed by
   the Tier 2 release if the fingerprint agrees with the tag's artist and the release is not
   secondary ("Day Tripper" under "Stone Free"); "Imagine" credited to the Beatles only on a bootleg
   stays on the tag's album.
7. **Number and title from the releases.** A track of a tagged album without a number takes the most
   frequent position in the releases whose title matches the album; a title made only of a number
   ("03") takes the title at that position, unless that is a number too (System Of A Down "36"
   stays).
8. **Split albums, by tracklist.** An album grouped from tags with the same artist and title as a
   Tier 2 release takes its year and spelling if at least one of its songs is in that release's
   tracklist, at any distance in years (remasters are merged). A window of years was discarded with
   the user: two different albums with the same name ("Weezer" 1994 and 2001, the four "Peter
   Gabriel") would be merged.
9. **Year from the name search.** Without a year in the tags, or with tracks that disagree, the first
   release date of the release group found by artist and title applies; the name comparison ignores
   "&"/"and", a leading article and punctuation, and the title takes the MusicBrainz spelling.
10. **Full-length duration** for truncated files: ADR-0023, point 6.
11. **Isolated associations.** A recording associated with the fingerprint fewer than 10 times and
    less than 1% as often as the most associated one (AcoustID "sources") is discarded. "Sliver" (134
    s) was linked with the same score to the studio recording (2297 associations) and to a live one at
    the Paramount (2), and the live one won on the bonus disc of "Nevermind"; it now goes to the 1990
    single ("Incesticide" is a compilation and loses, the user's choice). A first version of this
    rule, keeping only the recordings within 2 s of the duration closest to the file, moved "Holy
    Diver", "Hey Jude", "Sugar Sugar" and about twenty more onto compilations: MusicBrainz often has
    several recordings of the same master with durations 2-4 s apart. Replaced with the user's
    agreement.

Tried and discarded in the loop: preferring the identical title (six regressions, "The Bard's Song"
ended up on a DVD) and the reserve when the tag artist's releases are all secondary (covers on
compilations and live records ended up on the original: the Foo Fighters' "Tiny Dancer" on Elton
John).

## Discarded alternatives

- **Threshold on the AcoustID score**: the contamination is inside clusters with score 0.97-1.0, not
  separable by score.
- **Only the first recording in the list**: the order inside a cluster is not a measure of
  reliability (in "Bed of Roses" Andy Williams is sixth of 12, the Canon third).

## Consequences

- The `FingerprintLookupPort` changes shape; `MultiTrackConsensus` inherits the filter because it
  works on the candidate releases, and its tests and the fakes of the Tier 2 integration tests must
  be adapted.
- The "artist folder treated as an album" damage (`YYYY - Bon Jovi`) is **not** solved by this ADR:
  the years become plausible and the unrelated releases disappear, but the fragmentation by year
  stays until that point is addressed.
- Thresholds (15 s, 0.85, half of the candidates) tuned on a single real archive: to be checked again
  on the next runs.
