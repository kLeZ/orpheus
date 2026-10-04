package org.orpheus.acoustid.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Body of the response of {@code GET /v2/lookup} of the AcoustID API (SPEC.md section 4.4). The
 * {@code status} field ("ok"/"error") is not used by this adapter: an AcoustID application error comes
 * anyway with a non-2xx HTTP status, which the REST client turns into an exception handled upstream, so
 * mapping {@code results} is enough here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcoustIdLookupResponse(String status, List<AcoustIdResultDto> results) {
}
