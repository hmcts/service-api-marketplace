package uk.gov.hmcts.cp.domain;

import java.util.List;

public record ApplicationDetailResponse(ApplicationView application, List<ApiKeySummary> apiKeys) {
}
