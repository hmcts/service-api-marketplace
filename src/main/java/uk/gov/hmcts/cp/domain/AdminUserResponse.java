package uk.gov.hmcts.cp.domain;

/** A person as the admin queue shows them. */
public record AdminUserResponse(Integer id, String email, String name, String status, String domain,
                                String organisation, String requestedRole) {
}
