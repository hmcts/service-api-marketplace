package uk.gov.hmcts.amp.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.amp.filters.TracingFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AuditIngestionAppenderTest {

    private static final Map<String, String> FIELDS = Map.of(
        "audit_occurred_at", "2026-11-18T10:15:02Z",
        "audit_action", "CONSUMER_SUBSCRIPTION_KEY_CREATED",
        "audit_outcome", "SUCCESS",
        "audit_status", "201",
        "audit_actor_oid", "9b1c4e2a-0000-0000-0000-000000000001",
        "audit_target", "sub-123",
        "audit_endpoint", "POST /apim/subscription-keys",
        "audit_api", "slc",
        TracingFilter.MDC_CORRELATION_ID, "corr-1");

    private final List<List<Map<String, Object>>> uploads = new ArrayList<>();
    private final Logger fallbackLogger = (Logger) LoggerFactory.getLogger(AuditIngestionAppender.FALLBACK_LOGGER);
    private final ListAppender<ILoggingEvent> fallback = new ListAppender<>();

    @BeforeEach
    void captureFallback() {
        fallback.start();
        fallbackLogger.addAppender(fallback);
    }

    @AfterEach
    void stopCapturing() {
        fallbackLogger.detachAppender(fallback);
    }

    @Test
    void append_should_send_one_row_with_every_audit_field_mapped_to_its_column() {
        start(new AuditIngestionAppender(uploads::add, "AAT")).doAppend(event(FIELDS));

        assertThat(uploads).hasSize(1);
        assertThat(uploads.getFirst()).singleElement().satisfies(row -> assertThat(row).containsExactly(
            entry("TimeGenerated", "2026-11-18T10:15:02Z"),
            entry("Action", "CONSUMER_SUBSCRIPTION_KEY_CREATED"),
            entry("Outcome", "SUCCESS"),
            entry("Status", 201),
            entry("ActorOid", "9b1c4e2a-0000-0000-0000-000000000001"),
            entry("Target", "sub-123"),
            entry("Endpoint", "POST /apim/subscription-keys"),
            entry("Api", "slc"),
            entry("CorrelationId", "corr-1"),
            entry("Environment", "AAT")));
        assertThat(fallback.list).isEmpty();
    }

    @Test
    void append_should_leave_out_fields_the_entry_does_not_have() {
        start(new AuditIngestionAppender(uploads::add, "AAT")).doAppend(event(Map.of(
            "audit_action", "ADMIN_USERS_LISTED", "audit_status", "not-a-number")));

        assertThat(uploads.getFirst().getFirst())
            .containsOnlyKeys("Action", "Environment")
            .containsEntry("Action", "ADMIN_USERS_LISTED");
    }

    @Test
    void append_should_write_the_entry_to_stdout_at_error_when_the_upload_fails() {
        final AuditUploader failing = rows -> {
            throw new IllegalStateException("ingestion unavailable");
        };

        start(new AuditIngestionAppender(failing, "AAT")).doAppend(event(FIELDS));

        assertThat(fallback.list).singleElement().satisfies(line -> {
            assertThat(line.getLevel()).isEqualTo(Level.ERROR);
            assertThat(line.getFormattedMessage()).contains("CONSUMER_SUBSCRIPTION_KEY_CREATED sub-123 SUCCESS");
            assertThat(line.getMDCPropertyMap())
                .containsEntry("unsent_audit_action", "CONSUMER_SUBSCRIPTION_KEY_CREATED")
                .containsEntry("unsent_audit_actor_oid", "9b1c4e2a-0000-0000-0000-000000000001")
                .containsEntry(TracingFilter.MDC_CORRELATION_ID, "corr-1")
                .doesNotContainKey("audit_action");
            assertThat(line.getThrowableProxy().getMessage()).isEqualTo("ingestion unavailable");
        });
    }

    private static AuditIngestionAppender start(final AuditIngestionAppender appender) {
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        return appender;
    }

    private static ILoggingEvent event(final Map<String, String> fields) {
        final LoggingEvent event = new LoggingEvent();
        event.setLoggerName(AuditInterceptor.AUDIT_LOGGER);
        event.setLevel(Level.INFO);
        event.setMessage("CONSUMER_SUBSCRIPTION_KEY_CREATED sub-123 SUCCESS");
        event.setMDCPropertyMap(fields);
        return event;
    }
}
