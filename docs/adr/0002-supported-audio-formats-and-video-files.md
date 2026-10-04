# ADR-002: Supported audio formats and files with a video track

**Status**: accepted, 2026-07-15.

## Context

The real library contains formats never discussed explicitly before (WAV, WMA) and some video files
that ended up in the music hierarchy by mistake. The list of first-class formats needed widening, and
a rule was needed for ambiguous containers such as MP4, which can hold pure audio or video.

## Decision

The first-class audio formats go from three (MP3, FLAC, OGG) to six, adding WAV, WMA and M4A.

A file with a video track inside, whatever the container, is never included automatically: it is
reported, and including it requires explicit confirmation.

## Consequences

Support for the three added formats was verified in practice, not just declared, by reading real tags
from WMA files already in the library.

What decides for video files is the presence of a video track, not the extension: an MP4 file
without a video track is legitimate audio (M4A) and is treated as such.
