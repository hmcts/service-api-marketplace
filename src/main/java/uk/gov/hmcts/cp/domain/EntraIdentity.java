package uk.gov.hmcts.cp.domain;

/** Who a validated Entra token says the caller is. */
public record EntraIdentity(String oid, String tenantId, String email, String name) {
}
