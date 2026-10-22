package uk.gov.hmcts.amp.domain;

/** A role on the resource app that defines it. Both halves are needed to assign anything. */
public record ApiGrant(String shortCode, String resourceAppId, String role) {
}
