package uk.gov.hmcts.amp.audit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.hmcts.amp.filters.TracingFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** An entry that cannot be sent goes to stdout at ERROR, so it still reaches AppTraces. */
public class AuditIngestionAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    public static final String FALLBACK_LOGGER = "AUDIT_FALLBACK";

    private static final Logger FALLBACK = LoggerFactory.getLogger(FALLBACK_LOGGER);
    private static final Pattern STATUS = Pattern.compile("\\d{3}");

    private final AuditUploader uploader;
    private final String environment;

    public AuditIngestionAppender(final AuditUploader uploader, final String environment) {
        super();
        this.uploader = uploader;
        this.environment = environment;
    }

    @Override
    protected void append(final ILoggingEvent event) {
        final Map<String, String> fields = event.getMDCPropertyMap();
        try {
            uploader.upload(List.of(toRow(fields)));
        } catch (RuntimeException e) {
            fallBack(event, fields, e);
        }
    }

    Map<String, Object> toRow(final Map<String, String> fields) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("TimeGenerated", fields.get("audit_occurred_at"));
        row.put("Action", fields.get("audit_action"));
        row.put("Outcome", fields.get("audit_outcome"));
        row.put("Status", status(fields.get("audit_status")));
        row.put("ActorOid", fields.get("audit_actor_oid"));
        row.put("Target", fields.get("audit_target"));
        row.put("Endpoint", fields.get("audit_endpoint"));
        row.put("Api", fields.get("audit_api"));
        row.put("CorrelationId", fields.get(TracingFilter.MDC_CORRELATION_ID));
        row.put("Environment", environment);
        row.values().removeIf(Objects::isNull);
        return row;
    }

    private static Integer status(final String value) {
        return value != null && STATUS.matcher(value).matches() ? Integer.valueOf(value) : null;
    }

    static Map<String, String> unsent(final Map<String, String> fields) {
        final Map<String, String> renamed = new LinkedHashMap<>();
        fields.forEach((key, value) -> renamed.put(key.startsWith("audit_") ? "unsent_" + key : key, value));
        return renamed;
    }

    private void fallBack(final ILoggingEvent event, final Map<String, String> fields, final RuntimeException cause) {
        // Runs on the AsyncAppender's thread, which has no MDC of its own. The audit fields are
        // renamed unsent_audit_*: where audit has its own table, App Insights drops log lines that
        // carry audit_action, and this line must still reach AppTraces.
        final Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.setContextMap(unsent(fields));
        try {
            FALLBACK.error("Audit entry not sent to AmpAudit_CL: {}", event.getFormattedMessage(), cause);
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
