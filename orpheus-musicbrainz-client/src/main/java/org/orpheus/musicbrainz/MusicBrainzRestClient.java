package org.orpheus.musicbrainz;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.orpheus.musicbrainz.dto.*;

/**
 * Declarative client for the MusicBrainz web service (ADR-014). Timeouts only on transient network
 * failures; retries with backoff are handled by the adapters (e.g. {@link MusicBrainzReleaseLookup}),
 * so that every attempt goes through the rate limiter.
 */
@RegisterRestClient(configKey = "musicbrainz-api")
@RegisterClientHeaders(MusicBrainzUserAgentHeadersFactory.class)
public interface MusicBrainzRestClient {

	@GET
	@Path("/ws/2/recording/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	RecordingLookupResponseDto lookup(@PathParam("id") String recordingId, @QueryParam("inc") String inc, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/release-group/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	GenresDto releaseGroup(@PathParam("id") String releaseGroupId, @QueryParam("inc") String inc, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/artist/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	GenresDto artist(@PathParam("id") String artistId, @QueryParam("inc") String inc, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/release-group")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	ReleaseGroupSearchDto searchReleaseGroups(@QueryParam("query") String query, @QueryParam("limit") int limit, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/recording")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	RecordingSearchDto searchRecordings(@QueryParam("query") String query, @QueryParam("limit") int limit, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/release/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	org.orpheus.musicbrainz.dto.ReleaseDto release(@PathParam("id") String releaseId, @QueryParam("inc") String inc, @QueryParam("fmt") String fmt);

	@GET
	@Path("/ws/2/recording/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Timeout(5000)
	RecordingRelationsDto relations(@PathParam("id") String recordingId, @QueryParam("inc") String inc, @QueryParam("fmt") String fmt);
}
