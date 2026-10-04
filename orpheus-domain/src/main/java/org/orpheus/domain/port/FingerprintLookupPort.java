package org.orpheus.domain.port;

import org.orpheus.domain.identification.CandidateRecordingFilter;
import org.orpheus.domain.identification.FingerprintLookupResult;
import org.orpheus.domain.model.Track;

/**
 * Audio fingerprint lookup, the role of AcoustID in the Tier 2 chain (SPEC.md section 4.4,
 * ADR-014): given a track, computes its fingerprint, matches it against a public fingerprint
 * database and returns the candidate MusicBrainz recordings, with title, artists and duration when
 * known, plus the duration of the file (ADR-0018). No candidate if the recording is not in the
 * database used (Tier 3 takes over then, outside the scope of this port). Choosing among the
 * candidates is not the job of this port: it is domain logic ({@link CandidateRecordingFilter}).
 */
public interface FingerprintLookupPort {

	FingerprintLookupResult lookup(Track track);

	/**
	 * The same lookup with a given duration instead of the file's (ADR-0023, intervention 4): the
	 * duration of the whole song for a truncated file. The result carries that duration as the file
	 * duration, so the filter of ADR-0018 compares the recordings with the song looked up.
	 */
	default FingerprintLookupResult lookupWithDuration(Track track, int durationSeconds) {
		return FingerprintLookupResult.empty();
	}
}
