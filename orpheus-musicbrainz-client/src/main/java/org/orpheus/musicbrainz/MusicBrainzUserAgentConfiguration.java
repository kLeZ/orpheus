package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The HTTP contact required in the {@code User-Agent} header towards MusicBrainz (ADR-014, MusicBrainz
 * usage policy): without a real contact the service may block the client. The value starts from the
 * static configuration but can be overridden at runtime with {@link #override(String)}, because a CLI
 * command must be able to set a per-run contact (e.g. the user's email address in that session) without
 * touching {@code application.properties}. Application-scoped CDI bean, a single instance: no
 * concurrency to coordinate beyond the visibility across threads given by the {@code volatile} field.
 */
@ApplicationScoped
public class MusicBrainzUserAgentConfiguration {

	private volatile String value;

	public MusicBrainzUserAgentConfiguration(
	                                         @ConfigProperty(name = "musicbrainz.user-agent", defaultValue = "Orpheus/0.1 (contact not configured)") String initialValue) {
		this.value = initialValue;
	}

	public String value() {
		return value;
	}

	public void override(String newValue) {
		this.value = newValue;
	}
}
