package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.time.LocalDateTime;

public record ProductionAccessResponse(String reference, String status, LocalDateTime submittedAt) {

    public static ProductionAccessResponse from(final ProductionRequestEntity request) {
        return new ProductionAccessResponse(request.getReference(), request.getStatus(),
            request.getSubmittedAt());
    }
}
