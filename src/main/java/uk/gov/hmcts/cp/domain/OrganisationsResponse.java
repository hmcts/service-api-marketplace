package uk.gov.hmcts.cp.domain;

import java.util.List;

/** All organisations. */
public record OrganisationsResponse(List<OrganisationResponse> organisations) {
}
