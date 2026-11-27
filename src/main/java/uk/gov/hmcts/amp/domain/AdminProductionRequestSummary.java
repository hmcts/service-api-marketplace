package uk.gov.hmcts.amp.domain;

import java.time.LocalDateTime;

public record AdminProductionRequestSummary(String reference, Long applicationId, String applicationName,
                                            String organisation, LocalDateTime submittedAt, String status) {
}
