package uk.gov.hmcts.amp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.time.LocalDateTime;

/** clientId is absent where the application has since been deleted; the request outlives it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductionAccessResponse(String reference, String status, LocalDateTime submittedAt,
                                       String clientId, String applicationName) {

    public static ProductionAccessResponse from(final ProductionRequestEntity request) {
        return new ProductionAccessResponse(request.getReference(), request.getStatus(),
            request.getSubmittedAt(),
            request.getApplication() == null ? null : request.getApplication().getClientId(),
            request.getApplicationName());
    }
}
