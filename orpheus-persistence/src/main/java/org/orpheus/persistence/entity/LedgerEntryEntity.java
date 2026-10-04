package org.orpheus.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.orpheus.domain.model.LedgerAction;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity extends PanacheEntityBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long id;

	@Column(nullable = false)
	public String source;

	@Column(nullable = false)
	public String destination;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public LedgerAction action;

	@Column(nullable = false)
	public String reason;

	@Column(nullable = false)
	public double confidence;

	@Column(name = "recorded_at", nullable = false)
	public Instant recordedAt;
}
