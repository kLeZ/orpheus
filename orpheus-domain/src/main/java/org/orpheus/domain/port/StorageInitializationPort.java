package org.orpheus.domain.port;

/**
 * Prepares the persistent storage (ledger and review queue) before first use (ADR-010). Kept apart
 * from the application startup on purpose: the import command must be able to check where the
 * storage will be written (never inside the source folder, SPEC.md section 2) before the file is
 * created.
 */
public interface StorageInitializationPort {

	void initialize();
}
