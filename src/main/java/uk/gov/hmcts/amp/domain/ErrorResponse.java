package uk.gov.hmcts.amp.domain;

import java.time.Instant;

public record ErrorResponse(String error, Instant timestamp, String traceId) {
}
