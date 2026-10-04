# ADR-0023: Damaged audio files: recognition and reporting

**Status**: accepted, 2026-10-04. Direction decided by the user (measures 1-3 of the evaluation of
2026-10-04, plus "safety guards to find these files and report them as audio quality"); the thresholds
are a calibration on the data, not a decision of the user, and are reported as such. Measure 4 (a new
AcoustID query with the duration of a recording found by name), postponed at first, was added the
same day (point 6), as was the SIT tone detector (point 7) after the user confirmed the 56k case.

## Context

The source contains damaged files of two families: copies gone wrong (missing frames, e.g. Bon Jovi
"Never Say Goodbye") and downloads from the 56k era (truncated, with gaps or with line noise). Until
ADR-0022 Orpheus did not tell them apart: a damaged file was placed like the others, without review
items (`AUDIO_QUALITY` arose only for unreadable files), and in some cases the damage prevented Tier 2
recognition.

## Check on the data

Real AcoustID lookups on the source files and on altered copies of Evanescence "Whisper":

- AcoustID filters by duration, with a measured window of about ±7 s, and does not recognize
  fingerprints that do not start at the beginning of the song. fpcalc reads only the first 120 s.
- CBR MP3 truncated at 150 s of 237: no result; with the real duration, 0.96. 30 s missing in the first
  120 s: no result, 0.64 with the real duration. 30 s of noise in the first 90 s: no result. Silent
  dropouts of 9 s in total: 0.935.
- VBR MP3 with a Xing header truncated halfway: fpcalc reports the header duration (214 s, 126
  decoded) and recognition works (0.971).
- The fpcalc "Empty fingerprint" files decoded first to WAV with ffmpeg: Incubus "Blood On The Ground"
  recognized at 0.94, System Of A Down "Spiders" at 0.99; "Chop Suey" (1509 frames lost) stays without
  a result.
- A lying header: about 35 Sentenced MP3s declare in the Xing header a longer duration than the real
  one (e.g. "The Suicider": 347 s declared, 224 decoded, ending in digital silence). The audio is
  intact, but with the header duration AcoustID finds nothing; with the decoded one it recognizes all 8
  tried, at 0.97-0.98.

Calibration of the detectors on the whole of `~/Musica` (2204 files: 2163 MP3, 24 FLAC, 14 OGG, 2 WMA,
1 WAV), full decode with ffmpeg:

- **Decoding errors.** 274 MP3s have at least one error line; 157 have 2, that is a single unreadable
  frame, almost always garbage at the edges of the file. With at least 40 lines (about 20 frames, half
  a second of audio) 16 files remain, including "Never Say Goodbye" (105 frames), Linkin Park "My
  December" (84), "Chop Suey" (1509), 6 tracks of Yes "In A Word". No FLAC or OGG has errors.
- **Truncation from the declared duration.** Decoded duration shorter than the declared one by more than
  5 s and the last 100 ms above -20 dBFS (the song breaks off, it does not end): 8 MP3s, including
  Nickelback "Figured You Out" (182 s of 228) and 5 tracks of Pain Of Salvation "Scarsicker" (e.g.
  "Scarsick", 80 s of 279). With the ending at -30 dBFS, Sentenced "With Bitterness And Joy" was also
  included, which is intact with a wrong header (ending at -27.9 dBFS).
- **Dropouts.** Digital silence (samples with |x| ≤ 1 at 11025 Hz mono) of at least 0.25 s and less
  than 5 s, more than 2 s from start and end, preceded by 20 ms above -20 dBFS (a hard cut, not a
  fade). 70 files have internal silences of at least 0.3 s, almost all musical pauses that fade into
  silence; the hard-cut condition leaves 2: Evanescence "Farther Away" (4 cuts of about 2 s at 1:24,
  1:45, 2:51, 3:13, the only true candidate) and Pain Of Salvation "Nightmist" in the regular edition
  of "Entropia" (a false positive: the same break is on the record, at -17.7 dBFS in one edition and
  -22.9 in the other, on either side of the threshold). The threshold is not robust: to be reread at
  the first further real case.

## Decision

1. **Fingerprint from decoded audio.** If fpcalc fails on the file, the file is decoded with ffmpeg to
   a temporary WAV (in the system temporary folder, never near the source, deleted right after) and
   the fingerprint is computed on that.
2. **Decoded duration as a second chance.** If the lookup finds nothing and the decoded duration differs
   from the one used by more than 5 s, the lookup is repeated with the decoded duration. The file
   duration passed to the ADR-0018 filter is that of the lookup that gave a result.
3. **AcoustID score kept.** The score of the AcoustID result goes with every candidate recording; for a
   track identified by Tier 2 it becomes the confidence of the ledger entry and a `FingerprintScore`
   signal in the reconstruction review item. No threshold: a match returned by AcoustID stays a match
   (0.64 was the right song).
4. **Integrity analysis of every file**, with a full decode, and an `AUDIO_QUALITY` review item with the
   `MissingAudio` signal (seconds of audio lost or suspect) when at least one of these holds:
   - decoding errors: MP3 at least 40 error lines; FLAC and OGG, which have a CRC per frame or page, at
     least one;
   - truncation: FLAC with a decoded duration shorter than the declared one by more than 5 s; MP3 the
     same, but only if the last 100 ms are above -20 dBFS; for every format, a decoded duration
     shorter by more than 15 s (the ADR-0018 tolerance) than that of the identified recording;
   - dropouts, as calibrated above.
5. **The intact copy keeps the name.** Among several files arriving at the same path (ADR-0019), the
   name without a suffix goes to the first file without defects; the damaged ones take the suffixes.
6. **Full-length duration for truncated files.** If the lookup ran without results, the recordings with
   the same artist (tag or folder) and the same title are searched on MusicBrainz and the lookup is
   repeated with their durations, the most frequent first, at most three, only if at least 7 s away
   from the one already tried. On the real run: "Hey Jude" (380 s of 423), "Michelle" (23 s of 161),
   Incubus "Calgone", Lit "Over My Head" (104 s of 220).
7. **Telephone line tones (SIT).** The error message of a call, ended up in downloads of the 56k era:
   three pure tones in ascending order (913.8 / 1370.6 / 1776.7 Hz; measured 920 / 1420 / 1780 Hz at -8
   dBFS in Evanescence "Farther Away", followed by about 2 s of digital silence). 50 ms windows with
   more than 90% of the energy in one of the three bands, at least 3 in a row, and the three bands in
   order within 1.5 s. On the library: 8 sequences in "Farther Away" (0:59, 1:22, 1:44, 2:05, 2:28,
   2:49, 3:12, 3:34), no other file; with two bands in any order the intro of Modena City Ramblers
   "Bella Ciao" also triggered. Pure tones in general appear in 438 songs: on their own they say
   nothing.

## Coverage by format

| Defect | MP3 | FLAC | OGG |
| ------ | --- | ---- | --- |
| Unreadable frames | yes, from about 20 frames | yes, from the first | yes, from the first |
| Truncation, unidentified file | only VBR with a header and an ending not in silence | yes | no: the declared duration is already the truncated one |
| Truncation, identified file | yes | yes | yes |
| Dropouts to digital silence | yes | yes | yes |
| Telephone line tones (SIT) | yes | yes | yes |
| Clicks and noise over the music | no | no | no |

Line noise over the music other than SIT tones (clicks, noise) is not detected: a click detector tried
in the calibration did not separate the noise from compressed, clipped rock.

## Discarded alternatives

- **Fingerprint on a window further into the song** to get around a damaged start: AcoustID does not
  recognize fingerprints that do not start at the beginning (tried at 30 s and at 90 s).
- **Fingerprint on the whole song** (`fpcalc -length 0`): no gain on files with noise or gaps.
- **Threshold on the AcoustID score**: discarded, the low matches tried were correct.

## Consequences

- A full decode per file: about 1 s of CPU per file, 2204 files in about 4 minutes on 12 processes; the
  result is cached like the external responses.
- The AcoustID cache moved to version 2 (the score was missing), then 3 (the associations of ADR-0018
  rule 11): each version redoes all lookups on the first run.
- The Sentenced files with a wrong header keep the wrong duration visible in players: it is not
  corrected by this decision.
- The thresholds are calibrated on a single library with few FLAC and OGG files.
