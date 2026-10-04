package org.orpheus.musicbrainz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Sets the {@code User-Agent} header on every call to MusicBrainz (ADR-014). It reads the value from
 * {@link MusicBrainzUserAgentConfiguration} by injection instead of a direct {@code @ConfigProperty}
 * here: only by going through the mutable bean does a contact override set at runtime by a CLI command
 * reach the following calls without rebuilding the REST client.
 * <p>
 * Application-scoped CDI bean (not a class with only a no-arg constructor): MicroProfile REST Client
 * requires it to guarantee that {@code @Inject} really works on the instance used by the generated
 * client.
 */
@ApplicationScoped
public class MusicBrainzUserAgentHeadersFactory implements ClientHeadersFactory {

	@Inject
	MusicBrainzUserAgentConfiguration configuration;

	@Override
	public MultivaluedMap<String, String> update(
	                                             MultivaluedMap<String, String> incomingHeaders, MultivaluedMap<String, String> clientOutgoingHeaders) {
		MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
		headers.putSingle("User-Agent", configuration.value());
		return headers;
	}
}
