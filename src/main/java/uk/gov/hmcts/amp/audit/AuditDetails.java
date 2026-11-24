package uk.gov.hmcts.amp.audit;

public record AuditDetails(String actor, String target, String api) {
}
