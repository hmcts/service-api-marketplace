package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One request in full, for the page that shows what was asked for. The list returns
 * ProductionAccessResponse instead: the account page renders only the reference, status and date,
 * and every use case it never shows would travel with it.
 *
 * <p>applicationName is the name as it stood when the request was made, so the request still reads
 * sensibly once the application it names has been deleted.
 */
public record ProductionAccessDetail(String reference, String status, LocalDateTime submittedAt,
                                     String applicationName, String clientId, String organisation,
                                     LocalDate goLiveDate, String callVolume,
                                     String dataRequest, String proposedOutputs,
                                     String organisationType, List<String> dataRecipients,
                                     String dataRecipientsExplanation, String dataCombined,
                                     String dataCombinedDescription) {

    public static ProductionAccessDetail from(final ProductionRequestEntity request) {
        return new ProductionAccessDetail(request.getReference(), request.getStatus(),
            request.getSubmittedAt(), request.getApplicationName(),
            request.getApplication() == null ? null : request.getApplication().getClientId(),
            request.getOrganisation(), request.getGoLiveDate(), request.getCallVolume(),
            request.getDataRequest(), request.getProposedOutputs(),
            request.getOrganisationType(), request.getDataRecipients(),
            request.getDataRecipientsExplanation(), request.getDataCombined(),
            request.getDataCombinedDescription());
    }
}
