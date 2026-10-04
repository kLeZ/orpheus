package org.orpheus.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.orpheus.domain.model.ReviewCategory;
import org.orpheus.domain.model.ProvenanceState;
import org.orpheus.domain.model.ReviewStatus;

@Entity
@Table(name = "review_item")
public class ReviewItemEntity extends PanacheEntityBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long id;

	@Column(name = "track_physical_path", nullable = false)
	public String trackPhysicalPath;

	@Column(name = "track_artist")
	public String trackArtist;

	@Column(name = "track_title")
	public String trackTitle;

	@Column(name = "track_album")
	public String trackAlbum;

	@Column(name = "track_album_artist")
	public String trackAlbumArtist;

	@Column(name = "track_year")
	public Integer trackYear;

	@Column(name = "track_number")
	public Integer trackNumber;

	@Column(name = "track_disc_number")
	public Integer trackDiscNumber;

	@Column(name = "track_musicbrainz_recording_id")
	public String trackMusicBrainzRecordingId;

	@Column(name = "track_musicbrainz_release_id")
	public String trackMusicBrainzReleaseId;

	@Column(name = "track_acoustid")
	public String trackAcoustId;

	@Column(name = "track_provenance_source", nullable = false)
	public String trackProvenanceSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "track_provenance_state", nullable = false)
	public ProvenanceState trackProvenanceState;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public ReviewCategory category;

	@Column(nullable = false)
	public String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public ReviewStatus status;

	@OneToMany(mappedBy = "reviewItem", cascade = CascadeType.ALL, orphanRemoval = true)
	public List<RawSignalEntity> signals = new ArrayList<>();
}
