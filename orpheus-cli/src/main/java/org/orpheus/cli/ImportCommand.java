package org.orpheus.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;
import org.orpheus.domain.identification.TextClassification;
import org.orpheus.domain.identification.Release;
import org.orpheus.domain.identification.TrackOutcome;
import org.orpheus.domain.identification.LocalIdentifier;
import org.orpheus.domain.identification.IdentificationPipeline;
import org.orpheus.domain.identification.CandidateRecording;
import org.orpheus.domain.ingest.CopyPlacement;
import org.orpheus.domain.ingest.AudioIntegrity;
import org.orpheus.domain.ingest.CanonicalArtistNames;
import org.orpheus.domain.ingest.PlaceholderNames;
import org.orpheus.domain.identification.ReleaseInfo;
import org.orpheus.domain.ingest.TagContext;
import org.orpheus.domain.ingest.GenresByName;
import org.orpheus.domain.ingest.TagRules;
import org.orpheus.domain.ingest.TagsToWrite;
import org.orpheus.domain.port.GenresPort;
import org.orpheus.domain.port.ReleaseGroupSearchPort;
import org.orpheus.domain.port.TagWriterPort;
import org.orpheus.domain.matching.ArtistComparison;
import org.orpheus.domain.ingest.DestinationPath;
import org.orpheus.domain.ingest.ReleaseUnification;
import org.orpheus.domain.model.Album;
import org.orpheus.domain.model.LedgerAction;
import org.orpheus.domain.model.ReviewCategory;
import org.orpheus.domain.model.ExternalIds;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.LedgerEntry;
import org.orpheus.domain.model.Provenance;
import org.orpheus.domain.model.RawSignal;
import org.orpheus.domain.model.ReviewItem;
import org.orpheus.domain.model.Placeholder;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.ReviewStatus;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.identification.CandidateRecordingFilter;
import org.orpheus.domain.port.AudioAnalysisPort;
import org.orpheus.domain.port.ComposersPort;
import org.orpheus.domain.port.TracklistPort;
import org.orpheus.domain.port.TextClassificationPort;
import org.orpheus.domain.port.ReviewQueuePort;
import org.orpheus.domain.port.AudioHashPort;
import org.orpheus.domain.port.StorageInitializationPort;
import org.orpheus.domain.port.TagReaderPort;
import org.orpheus.domain.port.LedgerPort;
import org.orpheus.domain.port.ReleaseLookupPort;
import org.orpheus.domain.port.FingerprintLookupPort;
import org.orpheus.domain.port.UnreadableTrackException;
import org.orpheus.musicbrainz.MusicBrainzUserAgentConfiguration;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * End-to-end Tier 0/1/2 import: scans a source folder, reads the tags, identifies albums by folder and
 * tags (SPEC.md 4.4), applies the multi-track consensus on the groups with placeholder tags (SPEC.md
 * 4.5, ADR-001) and the individual Tier 2 identification on the tracks and groups left without a usable
 * album tag, copies into an organized destination (ADR-003) and writes the ledger and the review queue.
 * The source is never touched (SPEC.md section 2).
 */
@TopCommand
@Dependent
@Command(
        name = "import", mixinStandardHelpOptions = true, description = "Imports a messy source folder into an organized destination (Tier 0/1/2).")
public class ImportCommand implements Callable<Integer> {

	private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "flac", "ogg", "wav", "wma", "m4a", "mp4");

	@Parameters(index = "0", description = "Source folder, never modified")
	public Path source;

	@Parameters(index = "1", description = "Organized destination folder")
	public Path destination;

	@Option(names = "--dry-run", description = "Computes destinations, ledger and review queue without copying any file")
	public boolean dryRun;

	@Option(names = "--musicbrainz-user-agent", description = "Overrides the HTTP User-Agent towards MusicBrainz for this run (ADR-014: the public API policy requires a real contact)")
	public @Nullable String musicbrainzUserAgent;

	private final Map<Path, Path> simulatedDestinations = new HashMap<>();

	@ConfigProperty(name = "orpheus.data-dir", defaultValue = ".")
	Path dataDir;

	// Placeholder names in the paths (PlaceholderNames): English defaults, configurable.
	@ConfigProperty(name = "orpheus.placeholders.unknown-artist")
	Optional<String> unknownArtistName;

	@ConfigProperty(name = "orpheus.placeholders.unknown-year")
	Optional<String> unknownYearName;

	@ConfigProperty(name = "orpheus.placeholders.unknown-track")
	Optional<String> unknownTrackName;

	@ConfigProperty(name = "orpheus.placeholders.unidentified")
	Optional<String> unidentifiedName;

	@ConfigProperty(name = "orpheus.placeholders.disc")
	Optional<String> discName;

	@ConfigProperty(name = "orpheus.placeholders.version")
	Optional<String> versionName;

	@ConfigProperty(name = "orpheus.cache-dir")
	Optional<Path> cacheDir;

	@Inject
	StorageInitializationPort storageInitialization;

	@Inject
	TagReaderPort tagReader;

	@Inject
	AudioHashPort audioHash;

	@Inject
	AudioAnalysisPort audioAnalysis;

	@Inject
	LedgerPort ledger;

	@Inject
	ReviewQueuePort reviewQueue;

	@Inject
	FingerprintLookupPort fingerprintLookup;

	@Inject
	ReleaseLookupPort releaseLookup;

	@Inject
	TextClassificationPort textClassification;

	@Inject
	MusicBrainzUserAgentConfiguration userAgentConfiguration;

	@Inject
	GenresPort genresPort;

	@Inject
	TagWriterPort tagWriter;

	@Inject
	ReleaseGroupSearchPort releaseGroupSearch;

	@Inject
	TracklistPort tracklist;

	@Inject
	ComposersPort composersPort;

	@Override
	public Integer call() {
		simulatedDestinations.clear();
		Path absoluteSource = source.toAbsolutePath().normalize();
		// The source must never be modified (SPEC.md section 2): no database, cache or destination inside
		// it.
		Map<String, Optional<Path>> toCheck = new LinkedHashMap<>();
		toCheck.put("The destination", Optional.of(destination));
		toCheck.put("The data folder (orpheus.data-dir)", Optional.of(dataDir));
		toCheck.put("The cache folder (orpheus.cache-dir)", cacheDir);
		for (var entry : toCheck.entrySet()) {
			if (entry.getValue().isPresent() && entry.getValue()
			                                         .get()
			                                         .toAbsolutePath()
			                                         .normalize()
			                                         .startsWith(absoluteSource)) {
				System.err.println(entry.getKey() + " (" + entry.getValue()
				                                                .get()
				                                                .toAbsolutePath()
				                                                .normalize() + ") is inside the source folder, which must never be modified: choose another one.");
				return 2;
			}
		}
		storageInitialization.initialize();

		if (musicbrainzUserAgent != null && !musicbrainzUserAgent.isBlank()) {
			userAgentConfiguration.override(musicbrainzUserAgent);
		}

		List<Path> files;
		try {
			files = listAudioFiles(source);
		} catch (IOException e) {
			System.err.println("Cannot scan the source folder: " + e.getMessage());
			return 1;
		}

		List<Track> tracks = new ArrayList<>();
		int unreadable = 0;
		for (Path f : files) {
			try {
				tracks.add(tagReader.read(f));
			} catch (UnreadableTrackException e) {
				reportUnreadable(f, e);
				unreadable++;
			}
		}

		Map<Path, Optional<AudioAnalysisPort.Analysis>> analyses = analyzeAll(tracks);

		List<TrackOutcome> outcomes = IdentificationPipeline.run(tracks, absoluteSource, fingerprintLookup, releaseLookup, textClassification);

		CanonicalArtistNames canonical = CanonicalArtistNames.empty();
		Map<String, Set<Integer>> discsByRelease = new HashMap<>();
		Map<Album, List<String>> creditsByGroup = new HashMap<>();
		ReleaseUnification unification = ReleaseUnification.of(outcomes.stream()
		                                                               .flatMap(e -> e.release().stream())
		                                                               .toList());
		outcomes = outcomes.stream()
		                   .map(e -> e.release().isEmpty() ? e : e.withRelease(unification.unified(e.release().get())))
		                   .toList();
		for (TrackOutcome outcome : outcomes) {
			canonical.registerLocal(outcome.album() != null && outcome.album().albumArtist() != null ? outcome.album()
			                                                                                                  .albumArtist() : localArtist(outcome.track()));
			outcome.release().filter(e -> e.info() != null).ifPresent(e -> {
				if (e.info().disc() != null) {
					discsByRelease.computeIfAbsent(e.releaseId(), k -> new HashSet<>()).add(e.info().disc());
				}
				if (outcome.album() != null) {
					creditsByGroup.computeIfAbsent(outcome.album(), k -> new ArrayList<>())
					              .add(e.info().artistCredit());
				}
				if (outcome.artistContrastingTag().isEmpty()) {
					canonical.register(localArtist(outcome.track()), e.info().artistCredit());
					canonical.register(outcome.track().metadata().albumArtist(), e.info().artistCredit());
				}
			});
		}

		// Already ordered albums never identified: search by name (ADR-0021, G01c). Before the paths,
		// because the MusicBrainz spelling of the artist counts as the canonical name (ADR-0020, point 3):
		// "Pain of Salvation" instead of the most frequent local variant "Pain Of Salvation".
		Map<Album, Optional<GenresByName.Choice>> choicesByName = new HashMap<>();
		int[] searchErrors = {
		                      0
		};
		for (TrackOutcome outcome : outcomes) {
			Album album = outcome.album();
			if (album == null || outcome.release().isPresent() || choicesByName.containsKey(album)) {
				continue;
			}
			choicesByName.put(album, GenresByName.orderedAlbumNames(album).flatMap(names -> {
				Optional<List<ReleaseGroupSearchPort.FoundReleaseGroup>> candidates = releaseGroupSearch.search(names.artist(), names.title());
				if (candidates.isEmpty()) {
					searchErrors[0]++;
				}
				Optional<GenresByName.Choice> choice = GenresByName.choose(names, candidates.orElse(List.of()));
				choice.ifPresent(c -> canonical.register(names.artist(), c.artistCredit()));
				return choice;
			}));
		}
		long searchedByName = choicesByName.keySet()
		                                   .stream()
		                                   .filter(a -> GenresByName.orderedAlbumNames(a).isPresent())
		                                   .count();

		// All the paths are computed before copying: the files that would reach the same path get a name of
		// their own, " (NN)" if the audio is identical, " (version NN)" if it differs; every file always
		// goes into the library (ADR-0019).
		scatteredCompilations = scatteredCompilations(outcomes);
		List<Destination> destinations = new ArrayList<>(outcomes.size());
		for (TrackOutcome outcome : outcomes) {
			destinations.add(destination(outcome, discsByRelease, canonical, creditsByGroup, unification, choicesByName));
		}
		List<Path> paths = destinations.stream().map(Destination::path).toList();
		GenresByTrack genres = new GenresByTrack(outcomes, destinations, canonical, choicesByName);
		genres.errors += searchErrors[0];
		Map<Integer, Optional<AudioHashPort.Hash>> hashes = new HashMap<>();
		List<TrackOutcome> finalOutcomes = outcomes;
		List<List<AudioIntegrity.Defect>> defects = outcomes.stream().map(e -> defectsOf(e, analyses)).toList();
		java.util.function.Function<Integer, Optional<AudioHashPort.Hash>> hashOf = k -> hashes.computeIfAbsent(k, x -> audioHash.hashOf(finalOutcomes.get(x)
		                                                                                                                                              .track()
		                                                                                                                                              .provenance()
		                                                                                                                                              .source()));
		List<CopyPlacement.Placement> placements = CopyPlacement.place(paths, (a, b) -> {
			Optional<AudioHashPort.Hash> ha = hashOf.apply(a);
			Optional<AudioHashPort.Hash> hb = hashOf.apply(b);
			return ha.isPresent() && hb.isPresent() && ha.get().hash().equals(hb.get().hash());
		}, placeholderNames().version(), i -> !defects.get(i).isEmpty());

		int duplicates = 0;
		int copyErrors = 0;
		int tagsWritten = 0;
		int tagCorrections = 0;
		int tagErrors = 0;
		int damaged = 0;
		for (int i = 0; i < outcomes.size(); i++) {
			TrackOutcome outcome = outcomes.get(i);
			CopyPlacement.Placement placement = placements.get(i);
			Path requestedDestination = destination.resolve(placement.relativePath());
			try {
				@Nullable Path copied;
				if (outcome.release().isPresent()) {
					copied = processIdentifiedTrack(outcome, outcome.release().get(), requestedDestination);
				} else if (outcome.album() != null) {
					copied = copyAndRecordInAlbum(outcome.track(), outcome.album(), requestedDestination);
				} else {
					copied = copyAndRecordIsolated(outcome.track(), outcome.tier3Suggestion(), requestedDestination);
				}
				if (copied == null) {
					duplicates++;
					continue;
				}
				if (!defects.get(i).isEmpty()) {
					damaged++;
					recordDefects(outcome.track(), defects.get(i));
				}
				if (placement.type() != CopyPlacement.PlacementType.ORIGINAL) {
					recordCopy(outcome.track(), placement, outcomes.get(placement.reference()).track(), hashOf
					                                                                                          .apply(i), hashOf.apply(placement.reference()));
				}
				TagsToWrite tags = TagRules.apply(tagContext(outcome, destinations.get(i), discsByRelease, canonical, genres.genres(i), composersOf(outcome)));
				try {
					if (!dryRun) {
						tagWriter.write(copied, tags);
					}
					tagsWritten++;
					if (!tags.corrections().isEmpty()) {
						tagCorrections++;
						recordTagCorrections(outcome.track(), tags);
					}
				} catch (TagWriterPort.TagWriteException e) {
					tagErrors++;
					System.err.println("Error writing the tags of " + copied + ": " + e.getMessage());
				}
			} catch (IOException e) {
				copyErrors++;
				System.err.println("Error copying " + outcome.track().provenance().source() + ": " + e.getMessage());
			}
		}

		long distinctAlbums = outcomes.stream().map(TrackOutcome::album).filter(Objects::nonNull).distinct().count();
		long grouped = outcomes.stream().filter(e -> e.album() != null).count();
		long isolated = outcomes.stream().filter(e -> e.album() == null).count();

		System.out.printf(
		        "Audio files found: %d - unreadable: %d - copy errors: %d - albums: %d - grouped tracks: %d - isolated: %d - duplicates discarded: %d - tags %s: %d - with corrections: %d - tag write errors: %d - genre lookup errors: %d - albums searched by name: %d, with genres: %d - damaged files: %d - not analyzed: %d%n", files.size(), unreadable, copyErrors, distinctAlbums, grouped, isolated, duplicates, dryRun ? "computed" : "written", tagsWritten, tagCorrections, tagErrors, genres.errors, searchedByName, genres.withGenresByName.size(), damaged, analyses.values()
		                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            .stream()
		                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            .filter(Optional::isEmpty)
		                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            .count());
		return 0;
	}

	// Path and folder values the tags must repeat (ADR-0021, principle 1).
	private record Destination(Path path,
	                           @Nullable String folderArtist,
	                           @Nullable String albumTitle,
	                           @Nullable Integer albumYear) {
	}

	private Set<String> scatteredCompilations = Set.of();

	/**
	 * Compilations scattered in the source in one folder per performer ("Re-Machined", tribute to Deep
	 * Purple: one track in "Metallica/", one in "Iron Maiden/" and so on). The same album title under at
	 * least two artists is not enough (two same-named albums by different artists exist): a Various Artists
	 * release group with that exact title on MusicBrainz is needed too. User decision of 2026-10-04:
	 * compilations always go in "Various Artists" (ADR-0024).
	 */
	private Set<String> scatteredCompilations(List<TrackOutcome> outcomes) {
		Map<String, Set<String>> artistsByTitle = new HashMap<>();
		Map<String, String> titles = new HashMap<>();
		for (TrackOutcome e : outcomes) {
			Album album = e.album();
			if (album == null || album.title() == null || e.release().isPresent() || DestinationPath.VARIOUS_ARTISTS
			                                                                                                        .equalsIgnoreCase(String.valueOf(album.albumArtist()))) {
				continue;
			}
			String artist = album.albumArtist() != null ? album.albumArtist() : localArtist(e.track());
			if (artist == null) {
				continue;
			}
			String key = GenresByName.normalize(album.title());
			artistsByTitle.computeIfAbsent(key, k -> new HashSet<>()).add(GenresByName.normalize(artist));
			titles.putIfAbsent(key, album.title());
		}
		Set<String> compilations = new HashSet<>();
		for (var entry : artistsByTitle.entrySet()) {
			if (entry.getValue().size() < 2) {
				continue;
			}
			boolean onMusicBrainz = releaseGroupSearch.search(DestinationPath.VARIOUS_ARTISTS, titles.get(entry.getKey()))
			                                          .orElse(List.of())
			                                          .stream()
			                                          .anyMatch(rg -> GenresByName.normalize(rg.title())
			                                                                      .equals(entry.getKey()) && DestinationPath.VARIOUS_ARTISTS.equalsIgnoreCase(rg.artistCredit()
			                                                                                                                                                    .strip()));
			if (onMusicBrainz) {
				compilations.add(entry.getKey());
			}
		}
		return compilations;
	}

	private Destination destination(TrackOutcome outcome, Map<String, Set<Integer>> discsByRelease, CanonicalArtistNames canonical, Map<Album, List<String>> creditsByGroup, ReleaseUnification unification, Map<Album, Optional<GenresByName.Choice>> choicesByName) {
		Track track = outcome.track();
		if (outcome.release().isPresent()) {
			Release release = outcome.release().get();
			boolean multiDisc = discsByRelease.getOrDefault(release.releaseId(), Set.of()).size() > 1;
			// Compilations always go in "Various Artists", even with a single track (user decision of
			// 2026-10-04).
			Path path = DestinationPath.fromRelease(track, release, multiDisc, outcome.album(), canonical, true, placeholderNames());
			String artist = release.info() != null ? DestinationPath.releaseFolderArtist(track, release, canonical, true, placeholderNames()) : null;
			return new Destination(path, artist, null, null);
		}
		// A folder group in which the identified tracks reveal that the folder was the artist ("Skunk
		// Anansie/", files without tags): the remaining tracks are isolated tracks of that artist, not an
		// album named after it (ADR-0020).
		Album album = outcome.album();
		if (album != null && LocalIdentifier.placeholderOrEmptyAlbumTag(outcome.track()) && ArtistComparison.matches(album.title(), creditsByGroup.getOrDefault(album, List.of()))) {
			album = null;
		}
		if (album != null && scatteredCompilations.contains(GenresByName.normalize(String.valueOf(album.title())))) {
			album = new Album(album.title(), DestinationPath.VARIOUS_ARTISTS, album.year(), album.completeness(), album.tracks());
		}
		if (album != null) {
			String artist = canonical.canonical(album.albumArtist() != null ? album.albumArtist() : String.valueOf(localArtist(outcome.track())));
			List<String> titles = album.tracks()
			                           .stream()
			                           .map(t -> t.metadata().title())
			                           .filter(Objects::nonNull)
			                           .toList();
			Optional<Release> dominant = unification.dominantFor(artist, album.title(), titles, id -> tracklist.titles(id)
			                                                                                                   .orElse(List.of()));
			if (dominant.isPresent()) {
				String dominantTitle = dominant.get().info() != null ? dominant.get().info().title() : album.title();
				album = new Album(dominantTitle, album.albumArtist(), dominant.get().releaseDate().year(), album
				                                                                                                .completeness(), album.tracks());
			}
			// Album found by the search by name: the MusicBrainz spelling of the title, not that of one of the
			// tracks ("Enema of the State", not "Enema Of The State"), unless the local title points to another
			// edition (decisions of 2026-09-28).
			Optional<GenresByName.Choice> choice = choicesByName.getOrDefault(outcome.album(), Optional.empty());
			// With a dominant release already applied, title and year stay its own: the spelling of the
			// search by name would split the two folders again ("weezer" and "Weezer").
			if (choice.isPresent() && dominant.isEmpty()) {
				// Without a year in the tags, or with tracks saying different years ("Lullabies to Paralyze":
				// one 2004, one 2005), the year of the first release of the release group found (28 "Unknown
				// Year" albums on the run of 2026-10-04).
				long yearsInTags = album.tracks()
				                        .stream()
				                        .map(t -> t.metadata().year())
				                        .filter(Objects::nonNull)
				                        .distinct()
				                        .count();
				// Without a year in the tags, the year inferred by Tier 2 comes from a limited list of
				// releases: the date of the release group wins ("Greatest Hits" by Guns N' Roses, 2004).
				Integer albumYear = album.year() != null && yearsInTags == 1 ? album.year() : choice.get()
				                                                                                    .year()
				                                                                                    .orElse(album.year());
				album = new Album(GenresByName.albumTitle(album.title(), choice.get()), album.albumArtist(), albumYear, album.completeness(), album.tracks());
			}
		}
		Path path = DestinationPath.compute(track, album, canonical, placeholderNames());
		if (album == null) {
			return new Destination(path, DestinationPath.canonicalTrackOrPathArtist(track, canonical, placeholderNames()), null, null);
		}
		return new Destination(path, DestinationPath.albumFolderArtist(track, album, canonical, placeholderNames()), DestinationPath.albumFolderTitle(album), album.year());
	}

	private PlaceholderNames placeholderNames() {
		PlaceholderNames d = PlaceholderNames.DEFAULTS;
		return new PlaceholderNames(unknownArtistName.orElse(d.unknownArtist()), unknownYearName.orElse(d.unknownYear()), unknownTrackName.orElse(d.unknownTrack()), unidentifiedName.orElse(d.unidentified()), discName.orElse(d.disc()), versionName.orElse(d.version()));
	}

	// The authors of the recording (user decision of 2026-10-04: composer on every track). A track grouped
	// by tags has no recording: it is derived from the fingerprint, without changing its placement, only if
	// the fingerprint agrees with the tag artist.
	private List<String> composersOf(TrackOutcome outcome) {
		Optional<String> recording = outcome.release()
		                                    .map(Release::info)
		                                    .map(ReleaseInfo::recordingId)
		                                    .or(() -> outcome.recording().map(CandidateRecording::recordingId));
		if (recording.isEmpty()) {
			CandidateRecordingFilter.Result filtered = CandidateRecordingFilter.filter(outcome.track(), fingerprintLookup.lookup(outcome.track()));
			if (filtered.artistContrastingTag().isEmpty() && !filtered.survivors().isEmpty()) {
				recording = Optional.of(filtered.survivors().getFirst().recordingId());
			}
		}
		return recording.flatMap(composersPort::composers).orElse(List.of());
	}

	private static TagContext tagContext(TrackOutcome outcome, Destination destination, Map<String, Set<Integer>> discsByRelease, CanonicalArtistNames canonical, List<String> genres, List<String> composers) {
		Optional<Release> release = outcome.release().filter(e -> e.info() != null);
		boolean multiDisc = release.map(e -> discsByRelease.getOrDefault(e.releaseId(), Set.of()).size() > 1)
		                           .orElse(false);
		boolean ownedCompilation = release.isPresent() && DestinationPath.VARIOUS_ARTISTS.equals(destination.folderArtist());
		String tagArtist = outcome.track().originalMetadata().artist();
		String canonicalArtist = Placeholder.missingArtist(tagArtist) ? null : canonical.canonical(tagArtist.strip());
		return new TagContext(outcome.track(), release, ownedCompilation, multiDisc, destination.folderArtist(), destination.albumTitle(), destination.albumYear(), genres, canonicalArtist, composers);
	}

	// T26: one review item per file with the changed fields, so CLI and web interface can offer to go back
	// to the original values (kept in the file too, T24).
	private void recordTagCorrections(Track track, TagsToWrite tags) {
		String detail = tags.corrections()
		                    .stream()
		                    .map(c -> c.field() + ": " + String.join(" / ", c.before()) + " -> " + String.join(" / ", c.after()))
		                    .collect(java.util.stream.Collectors.joining("; "));
		reviewQueue.record(new ReviewItem(track, ReviewCategory.TAG_CORRECTION, "Tags corrected: " + detail, ReviewStatus.PENDING, List.of()));
	}

	// ADR-0019: an identical duplicate or an alternative version stays in the library with its suffix, and
	// the review queue says so ("if one has duplicates, one knows it, or wants to know it").
	private void recordCopy(Track track, CopyPlacement.Placement placement, Track reference, Optional<AudioHashPort.Hash> hash, Optional<AudioHashPort.Hash> referenceHash) {
		String other = reference.provenance().source().toString();
		if (placement.type() == CopyPlacement.PlacementType.IDENTICAL_DUPLICATE) {
			reviewQueue.record(new ReviewItem(track, ReviewCategory.IDENTICAL_DUPLICATE, "Same audio as " + other, ReviewStatus.PENDING, List.of()));
		} else {
			String durations = hash.flatMap(AudioHashPort.Hash::durationSeconds)
			                       .map(d -> d + " s")
			                       .orElse("unknown duration") + " against " + referenceHash.flatMap(AudioHashPort.Hash::durationSeconds)
			                                                                                .map(d -> d + " s")
			                                                                                .orElse("unknown duration");
			reviewQueue.record(new ReviewItem(track, ReviewCategory.ALTERNATIVE_VERSION, "Same path as " + other + " but different audio (" + durations + ")", ReviewStatus.PENDING, List.of()));
		}
	}

	private @Nullable Path copyAndRecordInAlbum(Track t, Album album, Path requestedDestination) throws IOException {
		Path source = t.provenance().source();

		Path actualDestination = copyFile(source, requestedDestination);
		boolean copied = actualDestination != null;
		LedgerAction action = !copied ? LedgerAction.DISCARDED_DUPLICATE : reconciledAcrossFolders(album) ? LedgerAction.RECONCILED : LedgerAction.IMPORTED_AS_IS;
		String reason = !copied ? "Identical content already in the destination: a real duplicate" : "Tier 0/1: grouped in \"" + album.title() + "\"";

		ledger.record(new LedgerEntry(source, actualDestination != null ? actualDestination : requestedDestination, action, reason, 1.0, Instant.now()));
		// A track without an album tag was looked up by Tier 2 without success: it stays in the folder group,
		// but it must not be passed off as identified (DESIGN.md, corollaries).
		if (copied && LocalIdentifier.placeholderOrEmptyAlbumTag(t)) {
			reviewQueue.record(new ReviewItem(t, ReviewCategory.UNIDENTIFIED, "Grouped by folder in \"" + album.title() + "\": no Tier 2 identification", ReviewStatus.PENDING, List.of()));
		}
		return actualDestination;
	}

	private @Nullable Path copyAndRecordIsolated(Track t, Optional<TextClassification> suggestion, Path requestedDestination) throws IOException {
		Path source = t.provenance().source();

		Path actualDestination = copyFile(source, requestedDestination);
		boolean copied = actualDestination != null;
		LedgerAction action = copied ? LedgerAction.IMPORTED_AS_IS : LedgerAction.DISCARDED_DUPLICATE;
		String reason = copied ? "Isolated track: no Tier 0/1 grouping found" : "Identical content already in the destination: a real duplicate";

		ledger.record(new LedgerEntry(source, actualDestination != null ? actualDestination : requestedDestination, action, reason, 1.0, Instant.now()));
		if (copied) {
			if (suggestion.isPresent()) {
				recordTier3Item(t, suggestion.get());
			} else {
				reviewQueue.record(new ReviewItem(t, ReviewCategory.UNIDENTIFIED, "Isolated track: no Tier 2 identification and no Tier 3 suggestion", ReviewStatus.PENDING, List.of()));
			}
		}
		return actualDestination;
	}

	private void recordTier3Item(Track track, TextClassification suggestion) {
		StringBuilder reason = new StringBuilder("Tier 3: suggestion from the text heuristic");
		suggestion.artist().ifPresent(a -> reason.append(" - artist: ").append(a));
		suggestion.title().ifPresent(ti -> reason.append(" - title: ").append(ti));
		reviewQueue.record(new ReviewItem(track, ReviewCategory.UNCERTAIN_IDENTIFICATION, reason.toString(), ReviewStatus.PENDING, List.of(new RawSignal.TextClassificationConfidence(suggestion.confidence()))));
	}

	// A track identified by Tier 2 (individually or forced by the multi-track consensus): the path comes
	// from the release, not from the Tier 0/1 group or from the source folder (corrective plan, phase 2;
	// corollary 2 of DESIGN.md). Before, the year of the release was written in the path of the whole
	// group, and a group with tracks from different records split into "YYYY - <group>" folders.
	private @Nullable Path processIdentifiedTrack(TrackOutcome outcome, Release release, Path requestedDestination) throws IOException {
		Track track = outcome.track();
		Track enrichedTrack = enrichIds(track, release);
		Path source = enrichedTrack.provenance().source();
		Path actualDestination = copyFile(source, requestedDestination);
		boolean copied = actualDestination != null;
		String mode = outcome.forced() ? "forced by the multi-track consensus" : "individual identification";
		String reason = copied ? "Tier 2: " + mode + " on release " + describeRelease(release) + contrastNote(track, outcome.artistContrastingTag()) : "Identical content already in the destination: a real duplicate";
		LedgerAction action = copied ? LedgerAction.RECONSTRUCTED : LedgerAction.DISCARDED_DUPLICATE;
		// The AcoustID score of the recording is the confidence of the identification (ADR-0023); without a
		// score, 1.0 as for the other actions.
		Optional<Double> score = outcome.recording()
		                                .flatMap(CandidateRecording::score)
		                                .filter(p -> p > 0.0 && p <= 1.0);

		ledger.record(new LedgerEntry(source, actualDestination != null ? actualDestination : requestedDestination, action, reason, score.orElse(1.0), Instant.now()));
		if (copied) {
			List<RawSignal> signals = score.<List<RawSignal>>map(p -> List.of(new RawSignal.FingerprintScore(p, p)))
			                               .orElse(List.of());
			reviewQueue.record(new ReviewItem(enrichedTrack, ReviewCategory.TIER2_RECONSTRUCTION, reason, ReviewStatus.PENDING, signals));
		}
		return actualDestination;
	}

	private static @Nullable String localArtist(Track t) {
		return t.metadata().artist() != null ? t.metadata().artist() : t.artistFromPath();
	}

	// The release artist in the reason makes it checkable from the ledger alone whether Tier 2 chose a
	// release by another artist (metric of the corrective plan, docs/ROADMAP.md).
	private static String describeRelease(Release release) {
		StringBuilder d = new StringBuilder(release.releaseId());
		if (!release.artists().isEmpty()) {
			d.append(" by \"").append(String.join(", ", release.artists())).append('"');
		}
		if (release.info() != null && !release.info().title().isBlank()) {
			d.append(" (\"").append(release.info().title()).append("\")");
		}
		return d.toString();
	}

	// ADR-0018 point C: the fingerprint was preferred to the tag artist, it must be declared.
	private static String contrastNote(Track track, Optional<String> contrast) {
		return contrast.map(a -> " - fingerprint contrasting the tag artist \"" + track.metadata()
		                                                                               .artist() + "\": identified as \"" + a + "\"")
		               .orElse("");
	}

	private static Track enrichIds(Track t, Release release) {
		String recordingId = release.info() != null && release.info().recordingId() != null ? release.info()
		                                                                                             .recordingId() : t.metadata()
		                                                                                                               .externalIds()
		                                                                                                               .musicBrainzRecordingId();
		ExternalIds enrichedIds = new ExternalIds(recordingId, release.releaseId(), t.metadata()
		                                                                             .externalIds()
		                                                                             .acoustId());
		TrackMetadata m = t.metadata();
		return t.withMetadata(new TrackMetadata(m.artist(), m.title(), m.album(), m.albumArtist(), m.year(), m.trackNumber(), m.discNumber(), enrichedIds));
	}

	// One full decoding per file (ADR-0023), in parallel up to the number of processors: it is CPU work in
	// ffmpeg processes, the virtual threads only wait for them.
	private Map<Path, Optional<AudioAnalysisPort.Analysis>> analyzeAll(List<Track> tracks) {
		Map<Path, Optional<AudioAnalysisPort.Analysis>> results = new java.util.concurrent.ConcurrentHashMap<>();
		java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(Runtime.getRuntime()
		                                                                                 .availableProcessors());
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			for (Track t : tracks) {
				Path file = t.provenance().source();
				executor.submit(() -> {
					slots.acquireUninterruptibly();
					try {
						results.put(file, audioAnalysis.analyze(file));
					} finally {
						slots.release();
					}
				});
			}
		}
		return results;
	}

	private static List<AudioIntegrity.Defect> defectsOf(TrackOutcome outcome, Map<Path, Optional<AudioAnalysisPort.Analysis>> analyses) {
		Path file = outcome.track().provenance().source();
		return analyses.getOrDefault(file, Optional.empty())
		               .map(a -> AudioIntegrity.assess(file, a, outcome.recording()
		                                                               .flatMap(CandidateRecording::durationSeconds)))
		               .orElse(List.of());
	}

	private void recordDefects(Track track, List<AudioIntegrity.Defect> defects) {
		String description = String.join("; ", defects.stream().map(AudioIntegrity.Defect::description).toList());
		List<RawSignal> signals = defects.stream()
		                                 .filter(d -> d.seconds() > 0)
		                                 .<RawSignal>map(d -> new RawSignal.MissingAudio(d.seconds(), 1.0))
		                                 .toList();
		reviewQueue.record(new ReviewItem(track, ReviewCategory.AUDIO_QUALITY, "Damaged file: " + description, ReviewStatus.PENDING, signals));
	}

	private void reportUnreadable(Path file, UnreadableTrackException e) {
		TrackMetadata emptyMetadata = new TrackMetadata(null, null, null, null, null, null, null, ExternalIds.unknown());
		Track placeholder = new Track(file, emptyMetadata, new Provenance(file, ProvenanceState.INTACT));
		reviewQueue.record(new ReviewItem(placeholder, ReviewCategory.AUDIO_QUALITY, e.getMessage(), ReviewStatus.PENDING, List.of()));
	}

	// A destination path already taken is NOT a duplicate by itself: it is only a collision of the path
	// computed by DestinationPath (e.g. two tracks with placeholder tags "Unknown Track" in the same album,
	// or corrupted track numbers that coincide: the base case of the domain, not a rare one). Treating it
	// as a duplicate without looking at the content would silently discard a real, different track:
	// exactly the silently wrong merge DESIGN.md forbids. Only byte-identical content is a real duplicate;
	// otherwise the file name is disambiguated with an increasing numeric suffix, never overwritten and
	// never discarded without a check.
	//
	// In a dry run (--dry-run) no file is copied: the destinations already assigned in this run stay in
	// memory with the source file that takes them, and collisions and duplicates are decided by comparing
	// that source, so the simulated ledger matches that of a real run.
	private @Nullable Path copyFile(Path source, Path requestedDestination) throws IOException {
		Path candidate = requestedDestination;
		int n = 2;
		while (taken(candidate)) {
			if (identicalContent(source, occupantOf(candidate))) {
				return null;
			}
			candidate = variant(requestedDestination, n++);
		}
		if (dryRun) {
			simulatedDestinations.put(candidate, source);
		} else {
			Files.createDirectories(candidate.getParent());
			Files.copy(source, candidate);
		}
		return candidate;
	}

	private boolean taken(Path candidate) {
		return simulatedDestinations.containsKey(candidate) || Files.exists(candidate);
	}

	private Path occupantOf(Path candidate) {
		return simulatedDestinations.getOrDefault(candidate, candidate);
	}

	private static Path variant(Path base, int n) {
		String fileName = base.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String body = dot >= 0 ? fileName.substring(0, dot) : fileName;
		String extension = dot >= 0 ? fileName.substring(dot) : "";
		return base.getParent().resolve(body + " (" + n + ")" + extension);
	}

	private static boolean identicalContent(Path a, Path b) throws IOException {
		if (Files.size(a) != Files.size(b)) {
			return false;
		}
		return java.util.Arrays.equals(sha256(a), sha256(b));
	}

	private static byte[] sha256(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must always be available in the JVM", e);
		}
		try (var in = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
		return digest.digest();
	}

	// Tier 1 reconciles tracks scattered over several source folders into a single album (SPEC.md 4.4):
	// the domain vocabulary tells this case (RECONCILED) apart from a simple grouping inside the same folder
	// (IMPORTED_AS_IS), even if the physical action, a copy, is the same in both cases.
	private static boolean reconciledAcrossFolders(Album album) {
		return album.tracks().stream().map(t -> t.provenance().source().getParent()).distinct().count() > 1;
	}

	private static List<Path> listAudioFiles(Path root) throws IOException {
		try (Stream<Path> stream = Files.walk(root)) {
			return stream.filter(Files::isRegularFile)
			             .filter(p -> SUPPORTED_EXTENSIONS.contains(extension(p)))
			             .sorted()
			             .toList();
		}
	}

	private static String extension(Path p) {
		String name = p.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	/**
	 * Genres of every track according to ADR-0021: G01 from the release group of the release, G02 from the
	 * album artist (or the track artist, for a compilation). A track without a release takes the release
	 * group of an identified track in the same destination folder, so an album has the same genre on all
	 * the tracks, otherwise the artist identified elsewhere with the same canonical name. Without either:
	 * no genre (G03).
	 */
	private final class GenresByTrack {

		private final List<TrackOutcome> outcomes;
		private final List<Destination> destinations;
		private final CanonicalArtistNames canonical;
		private final Map<Path, String> releaseGroupByFolder = new HashMap<>();
		private final Map<String, String> idByArtist = new HashMap<>();
		private final Map<String, List<String>> byReleaseGroup = new HashMap<>();
		private final Map<String, List<String>> byArtist = new HashMap<>();
		private final Map<Album, Optional<GenresByName.Choice>> choicesByAlbum;
		private final Set<Album> withGenresByName = new HashSet<>();
		private int errors;

		GenresByTrack(List<TrackOutcome> outcomes, List<Destination> destinations, CanonicalArtistNames canonical, Map<Album, Optional<GenresByName.Choice>> choicesByAlbum) {
			this.choicesByAlbum = choicesByAlbum;
			this.outcomes = outcomes;
			this.destinations = destinations;
			this.canonical = canonical;
			for (int i = 0; i < outcomes.size(); i++) {
				ReleaseInfo info = outcomes.get(i).release().map(Release::info).orElse(null);
				if (info == null) {
					continue;
				}
				if (info.releaseGroupId() != null) {
					releaseGroupByFolder.putIfAbsent(destinations.get(i).path().getParent(), info.releaseGroupId());
				}
				if (info.releaseArtistIds().size() == 1 && !various(info.artistCredit())) {
					idByArtist.putIfAbsent(key(info.artistCredit()), info.releaseArtistIds().getFirst());
				}
				if (info.trackArtistIds().size() == 1 && info.trackArtistCredit() != null) {
					idByArtist.putIfAbsent(key(info.trackArtistCredit()), info.trackArtistIds().getFirst());
				}
			}
		}

		List<String> genres(int i) {
			ReleaseInfo info = outcomes.get(i).release().map(Release::info).orElse(null);
			Destination d = destinations.get(i);
			// Only an album folder: in a folder of loose tracks the neighbor says nothing.
			String releaseGroup = info != null ? info.releaseGroupId() : d.albumTitle() != null ? releaseGroupByFolder.get(d.path()
			                                                                                                                .getParent()) : null;
			if (releaseGroup != null) {
				List<String> g = lookup(byReleaseGroup, releaseGroup, genresPort::releaseGroupGenres);
				if (!g.isEmpty()) {
					return g;
				}
			}
			// Already ordered and never identified album: search by name, only with an exact match.
			Album album = outcomes.get(i).album();
			if (info == null && album != null && d.albumTitle() != null) {
				Optional<GenresByName.Choice> choice = choiceByName(album);
				if (choice.isPresent()) {
					List<String> g = choice.get()
					                       .releaseGroupId()
					                       .map(id -> lookup(byReleaseGroup, id, genresPort::releaseGroupGenres))
					                       .orElse(List.of());
					if (g.isEmpty() && choice.get().artistId().isPresent()) {
						g = lookup(byArtist, choice.get().artistId().get(), genresPort::artistGenres);
					}
					if (!g.isEmpty()) {
						withGenresByName.add(album);
						return g;
					}
				}
			}
			String artist = artistId(info, d);
			return artist == null ? List.of() : lookup(byArtist, artist, genresPort::artistGenres);
		}

		private Optional<GenresByName.Choice> choiceByName(Album album) {
			return choicesByAlbum.getOrDefault(album, Optional.empty());
		}

		// A MusicBrainz error leaves the genre of the file (G03) like an empty answer, but it is counted:
		// otherwise a run limited by the service would look like a run without known genres.
		private List<String> lookup(Map<String, List<String>> memory, String id, java.util.function.Function<String, Optional<List<String>>> source) {
			List<String> known = memory.get(id);
			if (known != null) {
				return known;
			}
			Optional<List<String>> response = source.apply(id);
			if (response.isEmpty()) {
				errors++;
			}
			memory.put(id, response.orElse(List.of()));
			return memory.get(id);
		}

		private @Nullable String artistId(@Nullable ReleaseInfo info, Destination d) {
			if (info != null && info.releaseArtistIds().size() == 1 && !various(info.artistCredit())) {
				return info.releaseArtistIds().getFirst();
			}
			if (info != null && info.trackArtistIds().size() == 1) {
				return info.trackArtistIds().getFirst();
			}
			return d.folderArtist() == null || various(d.folderArtist()) ? null : idByArtist.get(key(d.folderArtist()));
		}

		private String key(String artist) {
			return canonical.canonical(artist).strip().toLowerCase(java.util.Locale.ROOT);
		}

		private static boolean various(String credit) {
			return credit.isBlank() || DestinationPath.VARIOUS_ARTISTS.equalsIgnoreCase(credit.strip());
		}
	}
}
