package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TracingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String correlationId = resolveCorrelationId(request);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return "/".equals(path)
            || path.startsWith("/health")
            || path.startsWith("/info")
            || path.startsWith("/prometheus");
    }

    private static String resolveCorrelationId(final HttpServletRequest request) {
        final String inbound = request.getHeader(CORRELATION_ID_HEADER);
        return inbound == null || inbound.isBlank() ? UUID.randomUUID().toString() : inbound;
    }
}
