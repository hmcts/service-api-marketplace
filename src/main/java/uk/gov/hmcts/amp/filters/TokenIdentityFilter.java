package uk.gov.hmcts.amp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

// The header stays the fallback until every caller sends a token.
@Slf4j
@RequiredArgsConstructor
public class TokenIdentityFilter extends OncePerRequestFilter {

    public static final String REQUESTING_USER_ID = "requestingUserId";
    private static final String BEARER = "Bearer ";
    private static final String OBJECT_ID_CLAIM = "oid";

    private final JwtDecoder decoder;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String tokenUser = verifiedUserOf(request);
        if (tokenUser == null) {
            filterChain.doFilter(request, response);
            return;
        }
        final String headerUser = request.getHeader(REQUESTING_USER_ID);
        if (headerUser != null && !tokenUser.equalsIgnoreCase(headerUser.trim())) {
            log.warn("requestingUserId does not match the caller's token; acting for the token's user");
        }
        filterChain.doFilter(new ActingFor(request, tokenUser), response);
    }

    private String verifiedUserOf(final HttpServletRequest request) {
        final String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        final Jwt token;
        try {
            token = decoder.decode(authorization.substring(BEARER.length()).trim());
        } catch (JwtException e) {
            log.warn("Caller token not accepted, using the requestingUserId header: {}", e.getMessage());
            return null;
        }
        final String objectId = token.getClaimAsString(OBJECT_ID_CLAIM);
        if (objectId == null || objectId.isBlank()) {
            log.warn("Caller token has no oid, using the requestingUserId header");
            return null;
        }
        return objectId.trim().toLowerCase(Locale.UK);
    }

    private static final class ActingFor extends HttpServletRequestWrapper {

        private final String user;

        ActingFor(final HttpServletRequest request, final String user) {
            super(request);
            this.user = user;
        }

        @Override
        public String getHeader(final String name) {
            return REQUESTING_USER_ID.equalsIgnoreCase(name) ? user : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            return REQUESTING_USER_ID.equalsIgnoreCase(name)
                ? Collections.enumeration(Set.of(user))
                : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            final Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.removeIf(REQUESTING_USER_ID::equalsIgnoreCase);
            names.add(REQUESTING_USER_ID);
            return Collections.enumeration(names);
        }
    }
}
