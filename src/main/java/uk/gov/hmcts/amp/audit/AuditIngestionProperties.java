package uk.gov.hmcts.amp.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit.ingestion")
public record AuditIngestionProperties(boolean enabled, String endpoint, String ruleId, String environment) {

    public static final String STREAM = "Custom-AmpAudit";

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank() && ruleId != null && !ruleId.isBlank();
    }
}
