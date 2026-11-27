package uk.gov.hmcts.amp.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraTokenDecoderTest {

    private static final String AUDIENCE = "the-web-app";
    private static final String ISSUER = "https://issuer.example/tenant/v2.0";
    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final RSAKey SIGNING_KEY = newKey();
    private static final WireMockServer KEYS = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        KEYS.start();
        KEYS.stubFor(get(urlPathEqualTo("/keys")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
    }

    @Mock
    private AdminAuthProperties admin;

    @InjectMocks
    private EntraTokenDecoder tokens;

    @AfterAll
    static void stopServingKeys() {
        KEYS.stop();
    }

    @Test
    void both_decoders_should_accept_a_token_for_this_application() {
        keysAt(KEYS.baseUrl() + "/keys");
        final String token = token(AUDIENCE, ISSUER, Instant.now().plusSeconds(600));

        assertThat(tokens.forThisApplication().decode(token).getClaimAsString("oid")).isEqualTo(ADA);
        assertThat(tokens.forThisApplicationWithin(TIMEOUT).decode(token).getClaimAsString("oid")).isEqualTo(ADA);
    }

    @Test
    void token_for_another_application_should_be_refused() {
        keysAt(KEYS.baseUrl() + "/keys");
        final String token = token("another-app", ISSUER, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> tokens.forThisApplicationWithin(TIMEOUT).decode(token))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("not issued for this application");
    }

    @Test
    void token_from_another_issuer_should_be_refused() {
        keysAt(KEYS.baseUrl() + "/keys");
        final String token = token(AUDIENCE, "https://elsewhere.example/v2.0", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> tokens.forThisApplicationWithin(TIMEOUT).decode(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void expired_token_should_be_refused() {
        keysAt(KEYS.baseUrl() + "/keys");
        final String token = token(AUDIENCE, ISSUER, Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> tokens.forThisApplicationWithin(TIMEOUT).decode(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void unreachable_key_set_should_refuse_the_token_rather_than_hang() {
        keysAt("http://127.0.0.1:1/keys");
        final String token = token(AUDIENCE, ISSUER, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> tokens.forThisApplicationWithin(TIMEOUT).decode(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void key_set_address_that_is_not_a_url_should_be_reported() {
        when(admin.jwkSetUri()).thenReturn("unknown-scheme://keys");

        assertThatThrownBy(() -> tokens.forThisApplicationWithin(TIMEOUT))
            .isInstanceOf(IllegalStateException.class);
    }

    private void keysAt(final String keySetUri) {
        when(admin.jwkSetUri()).thenReturn(keySetUri);
        when(admin.issuer()).thenReturn(ISSUER);
        when(admin.audience()).thenReturn(AUDIENCE);
    }

    private static String token(final String audience, final String issuer, final Instant expiry) {
        try {
            final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .claim("oid", ADA)
                    .issueTime(Date.from(expiry.minusSeconds(3600)))
                    .expirationTime(Date.from(expiry))
                    .build());
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("the-tenants-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
