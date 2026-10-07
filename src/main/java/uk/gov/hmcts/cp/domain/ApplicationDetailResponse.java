package uk.gov.hmcts.cp.domain;

import java.util.List;

/**
 * One application, its client secrets (never the secrets themselves), and the APIM Subscription Key it was
 * given for each API it is connected to. There are no subscriptions unless credentials are issued through APIM.
 */
public record ApplicationDetailResponse(
    ApplicationView application, List<ApiKeySummary> apiKeys, List<ApiSubscription> apiSubscriptions) {
}
