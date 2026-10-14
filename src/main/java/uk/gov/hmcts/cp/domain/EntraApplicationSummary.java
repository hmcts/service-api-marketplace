package uk.gov.hmcts.cp.domain;

/** One application the caller can see. */
public record EntraApplicationSummary(String name, String environment, String clientId) {
}
