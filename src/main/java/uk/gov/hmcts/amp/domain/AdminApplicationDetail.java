package uk.gov.hmcts.amp.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AdminApplicationDetail(Long id, String name, String clientId, LocalDateTime createdAt,
                                     AdminApplicationSummary.Owner owner, List<String> apis,
                                     AdminUserDetail.ProductionRequest productionRequest) {
}
