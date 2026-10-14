package uk.gov.hmcts.cp.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraIdentity;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks a Microsoft Entra External ID token and says who it is for. The signature is checked against the
 * tenant's published keys, and the issuer, audience, tenant and expiry must all be right. Nothing in the token is
 * trusted until then, and nothing the caller sends alongside it (a header, an id in a body) is used to say who they
 * are.
 *
 * <p>Like the other credentials in this service there is no default audience: until ENTRA_TOKEN_AUDIENCES names
 * the client ids this service accepts tokens for, every call answers 503 rather than accepting anyone's token.
 */
@Slf4j
@Service
public class EntraTokenVerifier {

    private static final String NOT_SET = "NOT_SET";
    private static final String BEARER = "Bearer ";
    private static final String NOT_SIGNED_IN = "Not signed in.";

    @Value("${ENTRA_TENANT_ID:d44f885c-4fac-47bf-afde-d7d861ec4d7b}")
    private String tenantId;

    // The sign-in host's name for the tenant: its keys are published at <this>.ciamlogin.com.
    @Value("${ENTRA_TENANT_SUBDOMAIN:hmctsextsbox}")
    private String tenantSubdomain;

    // Left empty, the issuer and key address are the ones the tenant's OpenID metadata gives; set to point a test or
    // a demo at a stand-in.
    @Value("${ENTRA_TOKEN_ISSUER:}")
    private String issuer;

    @Value("${ENTRA_TOKEN_JWKS_URI:}")
    private String jwksUri;

    // Comma-separated client ids a token may have been issued for.
    @Value("${ENTRA_TOKEN_AUDIENCES:NOT_SET}")
    private String audiences;

    private volatile ConfigurableJWTProcessor<SecurityContext> processor;

    /**
     * The caller named by a valid {@code Authorization: Bearer} token, or a 401 (503 if not configured).
     */
    public EntraIdentity verify(final String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER) || authorization.length() == BEARER.length()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN);
        }
        String token = authorization.substring(BEARER.length()).trim();
        JWTClaimsSet claims;
        try {
            claims = processor().process(token, null);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            // Why it failed is logged; the token is not, and the caller is only told it is not signed in.
            log.warn("Entra token refused: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN);
        }
        return new EntraIdentity(text(claims, "oid"), text(claims, "tid"), email(claims), text(claims, "name"));
    }

    private ConfigurableJWTProcessor<SecurityContext> processor() {
        ConfigurableJWTProcessor<SecurityContext> existing = processor;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (processor == null) {
                processor = build();
            }
            return processor;
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> build() {
        if (audiences == null || NOT_SET.equals(audiences) || audiences.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Entra token validation is not configured. Set ENTRA_TOKEN_AUDIENCES.");
        }
        try {
            return processorFor(JWKSourceBuilder.<SecurityContext>create(URI.create(jwksUri()).toURL())
                .retrying(true).build());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("ENTRA_TOKEN_JWKS_URI is not a valid address: " + jwksUri(), e);
        }
    }

    // Split from build() so a test can supply its own signing keys instead of fetching the tenant's.
    ConfigurableJWTProcessor<SecurityContext> processorFor(final JWKSource<SecurityContext> keys) {
        Set<String> accepted = Arrays.stream(audiences.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
        JWTClaimsSet exact = new JWTClaimsSet.Builder().issuer(issuer()).claim("tid", tenantId).build();

        DefaultJWTProcessor<SecurityContext> built = new DefaultJWTProcessor<>();
        built.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        built.setJWTClaimsSetVerifier(
            new DefaultJWTClaimsVerifier<>(accepted, exact, Set.of("exp", "oid", "tid"), null));
        return built;
    }

    private String issuer() {
        return issuer == null || issuer.isBlank()
            ? "https://" + tenantId + ".ciamlogin.com/" + tenantId + "/v2.0"
            : issuer;
    }

    private String jwksUri() {
        return jwksUri == null || jwksUri.isBlank()
            ? "https://" + tenantSubdomain + ".ciamlogin.com/" + tenantId + "/discovery/v2.0/keys"
            : jwksUri;
    }

    private static String text(final JWTClaimsSet claims, final String name) {
        Object value = claims.getClaim(name);
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    // Local accounts carry the address as "email" (once the optional claim is on) or in "emails"; failing both,
    // the sign-in name is the address for an email-and-password account.
    private static String email(final JWTClaimsSet claims) {
        String email = text(claims, "email");
        if (email != null) {
            return email;
        }
        Object emails = claims.getClaim("emails");
        if (emails instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String first) {
            return first;
        }
        return text(claims, "preferred_username");
    }
}
