package uk.gov.hmcts.cp.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraIdentity;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntraTokenVerifierTest {

    private static final String TENANT = "d44f885c-4fac-47bf-afde-d7d861ec4d7b";
    private static final String ISSUER = "https://" + TENANT + ".ciamlogin.com/" + TENANT + "/v2.0";
    private static final String AUDIENCE = "aedd508a-d053-4d15-ae9c-6a61a54370f6";

    private RSAKey key;
    private EntraTokenVerifier verifier;

    @BeforeEach
    void setUp() throws JOSEException {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        verifier = new EntraTokenVerifier();
        ReflectionTestUtils.setField(verifier, "tenantId", TENANT);
        ReflectionTestUtils.setField(verifier, "audiences", AUDIENCE);
        ReflectionTestUtils.setField(verifier, "processor",
            verifier.processorFor(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()))));
    }

    private JWTClaimsSet.Builder valid() {
        return new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject("per-app-subject")
            .claim("oid", "11111111-2222-3333-4444-555555555555")
            .claim("tid", TENANT)
            .claim("email", "Dee.Mo@Example.gov.uk")
            .claim("name", "Dee Mo")
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(600)));
    }

    private String token(final JWTClaimsSet claims, final RSAKey signer) throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(signer));
        return "Bearer " + jwt.serialize();
    }

    private void assertRefused(final String authorization, final HttpStatus status) {
        assertThatThrownBy(() -> verifier.verify(authorization))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void valid_token_names_the_caller() throws JOSEException {
        EntraIdentity identity = verifier.verify(token(valid().build(), key));

        assertThat(identity.oid()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(identity.tenantId()).isEqualTo(TENANT);
        assertThat(identity.email()).isEqualTo("Dee.Mo@Example.gov.uk");
        assertThat(identity.name()).isEqualTo("Dee Mo");
    }

    @Test
    void the_address_can_come_from_the_emails_claim_or_the_sign_in_name() throws JOSEException {
        EntraIdentity fromList = verifier.verify(token(valid().claim("email", null)
            .claim("emails", List.of("dee@example.gov.uk")).build(), key));
        EntraIdentity fromSignInName = verifier.verify(token(valid().claim("email", null)
            .claim("preferred_username", "dee@example.org").build(), key));

        assertThat(fromList.email()).isEqualTo("dee@example.gov.uk");
        assertThat(fromSignInName.email()).isEqualTo("dee@example.org");
    }

    @Test
    void no_token_or_not_a_bearer_token_is_not_signed_in() {
        assertRefused(null, HttpStatus.UNAUTHORIZED);
        assertRefused("", HttpStatus.UNAUTHORIZED);
        assertRefused("Bearer ", HttpStatus.UNAUTHORIZED);
        assertRefused("Basic abc", HttpStatus.UNAUTHORIZED);
        assertRefused("Bearer not-a-token", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void token_signed_with_someone_elses_key_is_refused() throws JOSEException {
        RSAKey other = new RSAKeyGenerator(2048).keyID("test-key").generate();

        assertRefused(token(valid().build(), other), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void the_wrong_issuer_audience_or_tenant_is_refused() throws JOSEException {
        assertRefused(token(valid().issuer("https://evil.example/v2.0").build(), key), HttpStatus.UNAUTHORIZED);
        assertRefused(token(valid().audience("some-other-client").build(), key), HttpStatus.UNAUTHORIZED);
        assertRefused(token(valid().claim("tid", "00000000-0000-0000-0000-000000000000").build(), key),
            HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expired_token_or_one_without_an_oid_is_refused() throws JOSEException {
        assertRefused(token(valid().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), key),
            HttpStatus.UNAUTHORIZED);
        assertRefused(token(valid().claim("oid", null).build(), key), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void with_no_audience_configured_nothing_is_accepted_and_the_caller_is_told_why() {
        EntraTokenVerifier unconfigured = new EntraTokenVerifier();

        assertThatThrownBy(() -> unconfigured.verify("Bearer abc"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
