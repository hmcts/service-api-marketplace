package uk.gov.hmcts.amp.audit;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import uk.gov.hmcts.amp.services.ClockService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    public static final String AUDIT_LOGGER = "AUDIT";
    public static final String REQUESTING_USER_ID_HEADER = "requestingUserId";

    private static final Logger AUDIT = LoggerFactory.getLogger(AUDIT_LOGGER);

    private final ClockService clockService;

    @Override
    public void afterCompletion(@Nonnull final HttpServletRequest request,
                                @Nonnull final HttpServletResponse response,
                                @Nonnull final Object handler,
                                final Exception ex) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(LogAudit.class)) {
            final AuditAction action = method.getMethodAnnotation(LogAudit.class).action();
            try {
                write(action, request, response.getStatus());
            } catch (RuntimeException e) {
                log.error("Audit write failed for {}", action, e);
            }
        }
    }

    private void write(final AuditAction action, final HttpServletRequest request, final int status) {
        final Object captured = request.getAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE);
        final AuditDetails details = captured instanceof AuditDetails auditDetails
            ? auditDetails
            : new AuditDetails(null, null, null);
        final String outcome = HttpStatusCode.valueOf(status).is2xxSuccessful() ? "SUCCESS" : "FAILURE";

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("audit_action", action.name());
        fields.put("audit_target", details.target());
        fields.put("audit_outcome", outcome);
        fields.put("audit_status", String.valueOf(status));
        fields.put("audit_actor_oid", details.actor() != null
            ? details.actor()
            : request.getHeader(REQUESTING_USER_ID_HEADER));
        fields.put("audit_endpoint", request.getMethod() + " "
            + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
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
