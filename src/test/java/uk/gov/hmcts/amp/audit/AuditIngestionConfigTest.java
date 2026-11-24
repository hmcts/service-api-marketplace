package uk.gov.hmcts.amp.audit;

import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditIngestionConfigTest {

    private static final AuditIngestionProperties CONFIGURED = new AuditIngestionProperties(
        true, "https://amp-marketplace-audit-aat.uksouth-1.ingest.monitor.azure.com", "dcr-0123", "AAT");

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditInterceptor.AUDIT_LOGGER);

    @Test
    void should_send_audit_entries_to_the_table_instead_of_stdout_until_destroyed() {
        final List<Map<String, Object>> sent = new CopyOnWriteArrayList<>();
        final AuditIngestionConfig config = new AuditIngestionConfig(CONFIGURED, sent::addAll);

        assertThat(auditLogger.isAdditive()).isFalse();
        MDC.put("audit_action", "ADMIN_USER_VIEWED");
        try {
            auditLogger.info("ADMIN_USER_VIEWED SUCCESS");
        } finally {
            MDC.remove("audit_action");
        }
        config.destroy();

        assertThat(sent).singleElement().satisfies(row -> assertThat(row).containsEntry("Action", "ADMIN_USER_VIEWED"));
        assertThat(auditLogger.isAdditive()).isTrue();
        assertThat(auditLogger.iteratorForAppenders().hasNext()).isFalse();
    }

    @Test
    void should_refuse_to_start_when_switched_on_without_the_rule_details() {
        final AuditIngestionProperties missing = new AuditIngestionProperties(true, "", null, "AAT");

        assertThatThrownBy(() -> new AuditIngestionConfig(missing, rows -> { }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AUDIT_INGESTION_ENDPOINT");
    }
}
