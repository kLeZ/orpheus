package org.orpheus.domain.port;

import org.orpheus.domain.model.LedgerEntry;

/**
 * Records a {@link LedgerEntry} in the ledger (SPEC.md section 8: it must always be possible to
 * rebuild the full history of an organized file, not only its final state). The persistence adapter
 * (ADR-010) implements this port; the domain knows nothing about SQLite or Hibernate.
 */
public interface LedgerPort {

	void record(LedgerEntry entry);
}
