package uk.gov.hmcts.amp.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

// The key set is named, not discovered, so startup does not depend on Entra.
@Component
@RequiredArgsConstructor
public class EntraTokenDecoder {

    private static final int KEY_SET_SIZE_LIMIT_BYTES = 50 * 1024;

    private final AdminAuthProperties admin;

    public JwtDecoder forThisApplication() {
        return validated(NimbusJwtDecoder.withJwkSetUri(admin.jwkSetUri()).build());
    }

    public JwtDecoder forThisApplicationWithin(final Duration keyFetchTimeout) {
        final int timeoutMillis = Math.toIntExact(keyFetchTimeout.toMillis());
        final JWKSource<SecurityContext> keys = JWKSourceBuilder.<SecurityContext>create(
                keySetUrl(), new DefaultResourceRetriever(timeoutMillis, timeoutMillis, KEY_SET_SIZE_LIMIT_BYTES))
            .build();
        return validated(NimbusJwtDecoder.withJwkSource(keys).build());
    }

    private NimbusJwtDecoder validated(final NimbusJwtDecoder decoder) {
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

    private URL keySetUrl() {
        try {
            return URI.create(admin.jwkSetUri()).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Not a usable signing key address: " + admin.jwkSetUri(), e);
        }
    }
}
