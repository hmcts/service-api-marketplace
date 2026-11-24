package uk.gov.hmcts.amp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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

/**
 * Admin endpoints are the only paths this service authenticates.
 *
 * <p>A caller of {@code /admin/**} must present a token this service verified itself - signature,
 * issuer and audience - carrying the {@code amp_admin} app role in its {@code roles} claim.
 * The role is assigned to people in Entra.
 *
 * <p>aat and preview have no Entra registration at all, so there is no tenant to verify against
 * and the gate does not apply there.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class AdminSecurityConfig {

    static final String ADMIN_PATHS = "/admin/**";

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(final HttpSecurity http,
                                                final AdminAuthProperties admin) throws Exception {
        http.securityMatcher(ADMIN_PATHS)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!admin.usable()) {
            log.warn("No Entra tenant or audience for this environment; {} keeps the role check it "
                + "has always made against the database and nothing more", ADMIN_PATHS);
            return http.authorizeHttpRequests(request -> request.anyRequest().permitAll()).build();
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
    public SecurityFilterChain openFilterChain(final HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            // SecurityHeadersFilter already sets these on every response, error responses included.
            .headers(headers -> headers.disable())
            .authorizeHttpRequests(request -> request.anyRequest().permitAll())
            .build();
    }

    /**
     * Reads the tenant's signing keys from its own discovery document, so no key material is held
     * here. {@code withIssuerLocation} fetches that document on the first admin request rather than
     * at startup.
     */
    private static JwtDecoder decoder(final AdminAuthProperties admin) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(admin.issuer()).build();
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
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
