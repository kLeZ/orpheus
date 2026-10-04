package org.orpheus.domain.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Search of release groups by artist and album title (ADR-0021, revision of 2026-09-27): it only
 * serves to find the genres of an already ordered and tagged album that Tier 2 never identified.
 * {@code Optional.empty()} means an error, not to be made permanent; an empty list is a valid
 * answer.
 */
public interface ReleaseGroupSearchPort {

	record FoundReleaseGroup(String id,
	                         String title,
	                         String artistCredit,
	                         List<String> artistIds,
	                         @Nullable String primaryType,
	                         List<String> secondaryTypes,
	                         @Nullable Integer year) {

		public FoundReleaseGroup {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(artistCredit, "artistCredit");
			artistIds = artistIds == null ? List.of() : List.copyOf(artistIds);
			secondaryTypes = secondaryTypes == null ? List.of() : List.copyOf(secondaryTypes);
		}

		public FoundReleaseGroup(String id, String title, String artistCredit, List<String> artistIds, @Nullable String primaryType, List<String> secondaryTypes) {
			this(id, title, artistCredit, artistIds, primaryType, secondaryTypes, null);
		}
	}

	Optional<List<FoundReleaseGroup>> search(String artist, String albumTitle);
}
