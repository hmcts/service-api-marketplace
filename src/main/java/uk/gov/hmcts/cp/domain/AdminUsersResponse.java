package uk.gov.hmcts.cp.domain;

import java.util.List;

/** People matching an admin query. */
public record AdminUsersResponse(List<AdminUserResponse> users) {
}
