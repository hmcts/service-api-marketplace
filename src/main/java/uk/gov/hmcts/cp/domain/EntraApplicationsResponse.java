package uk.gov.hmcts.cp.domain;

import java.util.List;

/** The caller's applications; only for an active user. */
public record EntraApplicationsResponse(List<EntraApplicationSummary> applications) {
}
