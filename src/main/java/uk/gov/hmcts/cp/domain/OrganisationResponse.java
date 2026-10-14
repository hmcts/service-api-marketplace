package uk.gov.hmcts.cp.domain;

import java.util.List;

/** An organisation and the email domains that identify it. */
public record OrganisationResponse(Integer id, String name, List<String> domains) {
}
