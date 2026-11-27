package uk.gov.hmcts.amp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import uk.gov.hmcts.amp.audit.AdminAccessDeniedHandler;
import uk.gov.hmcts.amp.filters.TokenIdentityFilter;

import java.time.Duration;
import java.util.Collection;

/**
 * A caller of {@code /admin/**} must present a token this service verified itself - signature,
 * issuer and audience - carrying the {@code amp_admin} app role in its {@code roles} claim.
 * The role is assigned to people in Entra.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class AdminSecurityConfig {

    static final String ADMIN_PATHS = "/admin/**";
    // Consumer endpoints never waited on Entra; past this they fall back to the header.
    private static final Duration KEY_FETCH_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    @Order(1)
    // S4502 is the CSRF rule; see the exemption below for why it does not apply here.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain adminFilterChain(final HttpSecurity http,
                                                final AdminAuthProperties admin,
                                                final EntraTokenDecoder tokens,
                                                final AdminAccessDeniedHandler accessDenied) throws Exception {
        http.securityMatcher(ADMIN_PATHS)
            // Exempt because this is a stateless API: nothing here reads a cookie or a session.
            .csrf(csrf -> csrf.ignoringRequestMatchers(ADMIN_PATHS))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!admin.usable()) {
            log.warn("No Entra tenant or audience for this environment; {} is closed here", ADMIN_PATHS);
            return http.authorizeHttpRequests(request -> request.anyRequest().denyAll()).build();
        }

        final String authority = "ROLE_" + admin.roleOrDefault();
        log.info("Admin endpoints require {} from issuer {}", authority, admin.issuer());
        return http
            .authorizeHttpRequests(request -> request.anyRequest().hasAuthority(authority))
            .exceptionHandling(handling -> handling.accessDeniedHandler(accessDenied))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(tokens.forThisApplication())
                .jwtAuthenticationConverter(roleConverter())))
            .build();
    }

    @Bean
    @Order(2)
    // S4502 is the CSRF rule; see the exemption below for why it does not apply here.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain openFilterChain(final HttpSecurity http,
                                               final AdminAuthProperties admin,
                                               final EntraTokenDecoder tokens) throws Exception {
        if (admin.usable()) {
            http.addFilterBefore(new TokenIdentityFilter(tokens.forThisApplicationWithin(KEY_FETCH_TIMEOUT)),
                AuthorizationFilter.class);
        } else {
            log.warn("No Entra tenant or audience for this environment; the signed-in user comes from the header");
        }
        return http
            // Disabled because this is a stateless API: nothing here reads a cookie or a session.
            .csrf(csrf -> csrf.disable())
            // SecurityHeadersFilter already sets these on every response, error responses included.
            .headers(headers -> headers.disable())
            .authorizeHttpRequests(request -> request.anyRequest().permitAll())
            .build();
    }

    /**
     * Entra puts app roles in {@code roles}; Spring reads {@code scope} unless told otherwise.
     */
    private static JwtAuthenticationConverter roleConverter() {
        final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            final Collection<GrantedAuthority> granted = authorities.convert(jwt);
            log.info("Admin token accepted  oid={}  roles={}", jwt.getClaimAsString("oid"),
                jwt.getClaimAsStringList("roles"));
            return granted;
        });
        return converter;
    }
}
