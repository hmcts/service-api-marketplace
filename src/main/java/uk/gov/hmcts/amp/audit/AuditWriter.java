package uk.gov.hmcts.amp.audit;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.amp.services.ClockService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The one place an audit line is written, so every one of them carries the same fields. */
@Component
@RequiredArgsConstructor
public class AuditWriter {

    public static final String AUDIT_LOGGER = "AUDIT";

    private static final Logger AUDIT = LoggerFactory.getLogger(AUDIT_LOGGER);

    private final ClockService clockService;

    public void write(final AuditAction action, final AuditDetails details, final String actorOid,
                      final int status, final String endpoint) {
        final String outcome = HttpStatusCode.valueOf(status).is2xxSuccessful() ? "SUCCESS" : "FAILURE";

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("audit_action", action.name());
        fields.put("audit_target", details.target());
        fields.put("audit_outcome", outcome);
        fields.put("audit_status", String.valueOf(status));
        fields.put("audit_actor_oid", details.actor() != null ? details.actor() : actorOid);
        fields.put("audit_endpoint", endpoint);
        fields.put("audit_api", details.api());
        fields.put("audit_occurred_at", clockService.now().toString());
        fields.values().removeIf(Objects::isNull);

        // Fields go via MDC: the canonical logback.xml emits MDC as JSON but has no arguments provider.
        fields.forEach(MDC::put);
        try {
            AUDIT.info("{} {}", details.target() == null ? action : action + " " + details.target(), outcome);
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }
}
