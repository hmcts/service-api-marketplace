package uk.gov.hmcts.amp.audit;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A refusal on an admin path is decided by the filter chain, which returns before any controller
 * runs, so {@link AuditInterceptor} never sees it. Without this, the only admin requests on record
 * would be the ones that succeeded.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler refusal = new AccessDeniedHandlerImpl();
    private final AuditWriter auditWriter;

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
                       final AccessDeniedException denied) throws IOException, ServletException {
        auditWriter.write(AuditAction.ADMIN_ACCESS_DENIED, new AuditDetails(null, null, null), oid(),
            HttpServletResponse.SC_FORBIDDEN, request.getMethod() + " " + request.getRequestURI());
        refusal.handle(request, response, denied);
    }

    private static String oid() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token && token.getToken() instanceof Jwt jwt
            ? jwt.getClaimAsString("oid")
            : null;
    }
}
