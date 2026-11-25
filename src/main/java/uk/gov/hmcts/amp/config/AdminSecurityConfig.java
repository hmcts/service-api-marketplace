package uk.gov.hmcts.amp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;

/**
 * Admin endpoints are the only paths this service authenticates.
 *
 * <p>A caller of {@code /admin/**} must present a token this service verified itself - signature,
 * issuer and audience - carrying the {@code amp_admin} app role in its {@code roles} claim.
 * The role is assigned to people in Entra.
 *
 * <p>aat and preview have no Entra registration, so there is no tenant to verify against and the
 * admin paths are closed there rather than left open. Nobody signs in to either: the web runs
 * with Entra disabled in both.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class AdminSecurityConfig {

    static final String ADMIN_PATHS = "/admin/**";

    @Bean
    @Order(1)
    // S4502 is the CSRF rule; see the exemption below for why it does not apply here.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain adminFilterChain(final HttpSecurity http,
                                                final AdminAuthProperties admin) throws Exception {
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
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(decoder(admin))
                .jwtAuthenticationConverter(roleConverter())))
            .build();
    }

    /** Every other path as it was: this service has never authenticated them and this does not start. */
    @Bean
    @Order(2)
    // S4502 is the CSRF rule; see the exemption below for why it does not apply here.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain openFilterChain(final HttpSecurity http) throws Exception {
        return http
            // Disabled because this is a stateless API: nothing here reads a cookie or a session.
            .csrf(csrf -> csrf.disable())
            // SecurityHeadersFilter already sets these on every response, error responses included.
            .headers(headers -> headers.disable())
            .authorizeHttpRequests(request -> request.anyRequest().permitAll())
            .build();
    }

    /**
     * Reads the tenant's signing keys from Entra, so no key material is held here. The keys are
     * named rather than discovered because {@code withIssuerLocation} fetches the discovery
     * document while the bean is being built, which would make the tenant's availability a
     * condition of this service starting at all.
     */
    private static JwtDecoder decoder(final AdminAuthProperties admin) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(admin.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(admin.issuer()),
            audienceIs(admin.audience())));
        return decoder;
    }

    /** A token minted for some other application in the same directory is not a token for us. */
    private static OAuth2TokenValidator<Jwt> audienceIs(final String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The token was not issued for this application.", null));
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
