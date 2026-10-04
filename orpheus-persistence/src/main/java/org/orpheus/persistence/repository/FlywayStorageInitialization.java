package org.orpheus.persistence.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.orpheus.domain.port.StorageInitializationPort;

/**
 * Applies the Flyway migrations on request instead of at startup
 * ({@code quarkus.flyway.migrate-at-start=false}): with the migration at startup the SQLite file was
 * created before the command could check its location, and a run launched from the source folder wrote
 * {@code orpheus.db} inside the source (it really happened in the run of 2026-09-16).
 */
@ApplicationScoped
public class FlywayStorageInitialization implements StorageInitializationPort {

	@Inject
	Flyway flyway;

	@Override
	public void initialize() {
		flyway.migrate();
	}
}
