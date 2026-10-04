package org.orpheus.persistence.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.orpheus.domain.model.TrackMetadata;
import org.orpheus.domain.model.RawSignal;
import org.orpheus.domain.model.ReviewItem;
import org.orpheus.domain.model.Track;
import org.orpheus.domain.port.ReviewQueuePort;
import org.orpheus.persistence.entity.ReviewItemEntity;
import org.orpheus.persistence.entity.RawSignalEntity;
import org.orpheus.persistence.entity.RawSignalType;

@ApplicationScoped
public class ReviewItemRepository implements PanacheRepositoryBase<ReviewItemEntity, Long>, ReviewQueuePort {

	@Override
	@Transactional
	public void record(ReviewItem item) {
		Track track = item.track();
		TrackMetadata metadata = track.metadata();

		ReviewItemEntity entity = new ReviewItemEntity();
		entity.trackPhysicalPath = track.physicalPath().toString();
		entity.trackArtist = metadata.artist();
		entity.trackTitle = metadata.title();
		entity.trackAlbum = metadata.album();
		entity.trackAlbumArtist = metadata.albumArtist();
		entity.trackYear = metadata.year();
		entity.trackNumber = metadata.trackNumber();
		entity.trackDiscNumber = metadata.discNumber();
		entity.trackMusicBrainzRecordingId = metadata.externalIds().musicBrainzRecordingId();
		entity.trackMusicBrainzReleaseId = metadata.externalIds().musicBrainzReleaseId();
		entity.trackAcoustId = metadata.externalIds().acoustId();
		entity.trackProvenanceSource = track.provenance().source().toString();
		entity.trackProvenanceState = track.provenance().state();
		entity.category = item.category();
		entity.reason = item.reason();
		entity.status = item.status();

		for (RawSignal signal : item.signals()) {
			RawSignalEntity signalEntity = new RawSignalEntity();
			signalEntity.reviewItem = entity;
			signalEntity.type = typeOf(signal);
			signalEntity.value = valueOf(signal);
			signalEntity.confidence = signal.confidence();
			entity.signals.add(signalEntity);
		}

		persist(entity);
	}

	private static RawSignalType typeOf(RawSignal signal) {
		return switch (signal) {
			case RawSignal.MatchDistance s -> RawSignalType.MATCH_DISTANCE;
			case RawSignal.DurationDeviation s -> RawSignalType.DURATION_DEVIATION;
			case RawSignal.SpectralAnomaly s -> RawSignalType.SPECTRAL_ANOMALY;
			case RawSignal.FingerprintScore s -> RawSignalType.FINGERPRINT_SCORE;
			case RawSignal.MissingAudio s -> RawSignalType.MISSING_AUDIO;
			case RawSignal.TextClassificationConfidence s -> RawSignalType.TEXT_CLASSIFICATION_CONFIDENCE;
		};
	}

	private static double valueOf(RawSignal signal) {
		return switch (signal) {
			case RawSignal.MatchDistance s -> s.distance();
			case RawSignal.DurationDeviation s -> s.ratio();
			case RawSignal.SpectralAnomaly s -> s.score();
			case RawSignal.FingerprintScore s -> s.score();
			case RawSignal.MissingAudio s -> s.seconds();
			case RawSignal.TextClassificationConfidence s -> 0.0;
		};
	}
}
