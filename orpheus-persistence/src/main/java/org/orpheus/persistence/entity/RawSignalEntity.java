package org.orpheus.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "raw_signal")
public class RawSignalEntity extends PanacheEntityBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "review_item_id", nullable = false)
	public ReviewItemEntity reviewItem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public RawSignalType type;

	@Column(nullable = false)
	public double value;

	@Column(nullable = false)
	public double confidence;
}
