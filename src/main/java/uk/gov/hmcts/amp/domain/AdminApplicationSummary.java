package uk.gov.hmcts.amp.domain;

import java.time.LocalDateTime;

public record AdminApplicationSummary(Long id, String name, Owner owner, long apiCount, LocalDateTime createdAt,
                                      boolean productionRequested) {

    public record Owner(Long id, String name, String email) {
    }
}
