package org.orpheus.domain.model;

import org.jspecify.annotations.Nullable;

public record ExternalIds(
                          @Nullable String musicBrainzRecordingId,
                          @Nullable String musicBrainzReleaseId,
                          @Nullable String acoustId) {

	public static final ExternalIds UNKNOWN = new ExternalIds(null, null, null);

	public static ExternalIds unknown() {
		return UNKNOWN;
	}
}
