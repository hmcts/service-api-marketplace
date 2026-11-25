package uk.gov.hmcts.amp.audit;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    public static final String AUDIT_LOGGER = AuditWriter.AUDIT_LOGGER;
    public static final String REQUESTING_USER_ID_HEADER = "requestingUserId";

    private final AuditWriter auditWriter;

    @Override
    public void afterCompletion(@Nonnull final HttpServletRequest request,
                                @Nonnull final HttpServletResponse response,
                                @Nonnull final Object handler,
                                final Exception ex) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(LogAudit.class)) {
            final AuditAction action = method.getMethodAnnotation(LogAudit.class).action();
            try {
                final Object captured = request.getAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE);
                final AuditDetails details = captured instanceof AuditDetails auditDetails
                    ? auditDetails
                    : new AuditDetails(null, null, null);
                auditWriter.write(action, details, actorOf(request),
                    response.getStatus(), request.getMethod() + " "
                        + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
            } catch (RuntimeException e) {
                log.error("Audit write failed for {}", action, e);
            }
        }
    }

    /** On an admin path, the oid from the verified token; elsewhere, the header the web app sends. */
    private static String actorOf(final HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString("oid");
        }
        return request.getHeader(REQUESTING_USER_ID_HEADER);
    }
}
