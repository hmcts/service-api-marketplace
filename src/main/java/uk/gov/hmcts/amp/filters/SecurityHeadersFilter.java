package uk.gov.hmcts.amp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Sets the OWASP-recommended security headers on every response, error responses included. The
 * service only ever answers JSON, so nothing it returns needs to load content, be framed, be read
 * cross-origin or be cached.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final Map<String, String> HEADERS = Map.of(
        "Strict-Transport-Security", "max-age=31536000; includeSubDomains",
        "X-Content-Type-Options", "nosniff",
        "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'",
        "Cross-Origin-Resource-Policy", "same-origin",
        "Permissions-Policy", "camera=(), microphone=(), geolocation=()",
        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate",
        "Pragma", "no-cache"
    );

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        HEADERS.forEach(response::setHeader);
        filterChain.doFilter(request, response);
    }
}
