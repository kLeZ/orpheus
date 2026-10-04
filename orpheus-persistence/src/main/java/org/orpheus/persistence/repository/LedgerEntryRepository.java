package org.orpheus.persistence.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.orpheus.domain.model.LedgerEntry;
import org.orpheus.domain.port.LedgerPort;
import org.orpheus.persistence.entity.LedgerEntryEntity;

@ApplicationScoped
public class LedgerEntryRepository implements PanacheRepositoryBase<LedgerEntryEntity, Long>, LedgerPort {

	@Override
	@Transactional
	public void record(LedgerEntry entry) {
		LedgerEntryEntity entity = new LedgerEntryEntity();
		entity.source = entry.source().toString();
		entity.destination = entry.destination().toString();
		entity.action = entry.action();
		entity.reason = entry.reason();
		entity.confidence = entry.confidence();
		entity.recordedAt = entry.recordedAt();
		persist(entity);
	}
}
