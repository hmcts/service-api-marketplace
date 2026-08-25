package uk.gov.hmcts.cp.domain;

import java.time.Instant;

public record ErrorResponse(String error, Instant timestamp, String traceId) {
}
