package uk.gov.hmcts.amp.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminProductionRequestDetail(String reference, String status, LocalDateTime submittedAt,
                                           AdminApplicationSummary.Owner requester, String organisation,
                                           Long applicationId, String applicationName, List<String> apis,
                                           LocalDate goLiveDate, String callVolume, String dataRequest,
                                           String proposedOutputs, String organisationType,
                                           List<String> dataRecipients, String dataRecipientsExplanation,
                                           String dataCombined, String dataCombinedDescription) {
}
