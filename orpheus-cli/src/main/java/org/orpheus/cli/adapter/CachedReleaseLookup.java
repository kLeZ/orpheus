package org.orpheus.cli.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.ReleaseDate;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.identification.ReleaseType;
import org.orpheus.domain.port.ReleaseLookupPort;

/** Cache decorator for the MusicBrainz releases of a recording (see {@link ExternalResponseCache}). */
@Decorator
@Priority(10)
public abstract class CachedReleaseLookup implements ReleaseLookupPort {

	// Version in the name: it must be increased when the shape or the meaning of what the adapter returns
	// changes, otherwise a check run would read old results without any error.
	private static final String CATEGORY = "musicbrainz-v4";

	record CachedInfo(String title,
	                  String artistCredit,
	                  @Nullable String recordingId,
	                  @Nullable Integer disc,
	                  @Nullable Integer number,
	                  @Nullable String trackTitle,
	                  @Nullable String releaseGroupId,
	                  @Nullable String trackArtistCredit,
	                  @Nullable List<String> trackArtistIds,
	                  @Nullable List<String> releaseArtistIds) {
	}

	record CachedRelease(String releaseId,
	                     ReleaseType type,
	                     int year,
	                     @Nullable Integer month,
	                     @Nullable Integer day,
	                     List<String> artists,
	                     boolean secondary,
	                     @Nullable CachedInfo info) {
	}

	@Inject
	@Delegate
	@Any
	ReleaseLookupPort delegate;

	@Inject
	ExternalResponseCache cache;

	@Override
	public List<Release> releasesFor(String recordingId) {
		if (!cache.enabled()) {
			return delegate.releasesFor(recordingId);
		}
		Optional<List<CachedRelease>> cached = cache.read(CATEGORY, recordingId, new TypeReference<>() {
		});
		if (cached.isPresent()) {
			return cached.get().stream().map(CachedReleaseLookup::fromCache).toList();
		}
		List<Release> releases = delegate.releasesFor(recordingId);
		if (!releases.isEmpty()) {
			cache.write(CATEGORY, recordingId, releases.stream().map(CachedReleaseLookup::toCache).toList());
		}
		return releases;
	}

	private static CachedRelease toCache(Release e) {
		ReleaseDate d = e.releaseDate();
		ReleaseInfo i = e.info();
		CachedInfo info = i == null ? null : new CachedInfo(i.title(), i.artistCredit(), i.recordingId(), i.disc(), i.number(), i.trackTitle(), i.releaseGroupId(), i.trackArtistCredit(), i.trackArtistIds(), i.releaseArtistIds());
		return new CachedRelease(e.releaseId(), e.type(), d.year(), d.month(), d.day(), e.artists(), e.secondary(), info);
	}

	private static Release fromCache(CachedRelease e) {
		ReleaseDate date = e.month() != null && e.day() != null ? ReleaseDate.exact(e.year(), e.month(), e.day()) : ReleaseDate.yearOnly(e.year());
		CachedInfo i = e.info();
		ReleaseInfo info = i == null ? null : new ReleaseInfo(i.title(), i.artistCredit(), i.recordingId(), i.disc(), i.number(), i.trackTitle(), i.releaseGroupId(), i.trackArtistCredit(), i.trackArtistIds(), i.releaseArtistIds());
		return new Release(e.releaseId(), e.type(), date, e.artists(), e.secondary(), info);
	}
}
