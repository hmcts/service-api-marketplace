package uk.gov.hmcts.amp.domain;

import java.time.LocalDateTime;
import java.util.List;

/** One user as the admin user page shows them, with what they have made and asked for. */
public record AdminUserDetail(Long id, String name, String email, String status, LocalDateTime createdAt,
                              LocalDateTime lastLoginAt, String organisation, List<Application> applications,
                              List<ProductionRequest> productionRequests) {

    public record Application(String name, long apiCount, LocalDateTime createdAt) {
    }

    public record ProductionRequest(String reference, String applicationName, String status,
                                    LocalDateTime submittedAt) {
    }
}