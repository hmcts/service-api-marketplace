package uk.gov.hmcts.cp.domain;

/** A client secret as listed afterwards: only its last four characters, never the secret. */
public record ApiKeySummary(String id, String preview, String createdAt, String revokedAt) {
}
