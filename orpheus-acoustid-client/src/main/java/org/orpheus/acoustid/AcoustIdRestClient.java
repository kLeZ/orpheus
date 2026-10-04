package org.orpheus.acoustid;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.orpheus.acoustid.dto.AcoustIdLookupResponse;

/**
 * Declarative client for the public AcoustID lookup API (ADR-014): no logic here, only the shape of the
 * request. Timeouts only on transient network failures; retries with backoff are handled by the adapter
 * {@link AcoustIdFingerprintLookup}, so that every attempt goes through the rate limiter.
 */
@RegisterRestClient(configKey = "acoustid-api")
public interface AcoustIdRestClient {

	@GET
	@Path("/v2/lookup")
	@Timeout(5000)
	AcoustIdLookupResponse lookup(
	                              @QueryParam("client") String client, @QueryParam("fingerprint") String fingerprint, @QueryParam("duration") int duration, @QueryParam("meta") String meta);
}
