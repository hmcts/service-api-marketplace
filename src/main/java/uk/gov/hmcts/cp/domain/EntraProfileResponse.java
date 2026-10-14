package uk.gov.hmcts.cp.domain;

/** The caller's profile as the marketplace holds it; available even while held. */
public record EntraProfileResponse(String oid, String email, String name, String organisation, String status,
                                   String role) {
}
