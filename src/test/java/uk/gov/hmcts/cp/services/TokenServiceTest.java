package uk.gov.hmcts.cp.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.UserEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    @Mock
    private ClockService clockService;

    private TokenService tokenService;

    private final UserEntity user = UserEntity.builder()
        .id(7).email("joe@example.com").role("producer").build();

    @BeforeEach
    void configure() {
        lenient().when(clockService.now()).thenReturn(NOW);
        tokenService = new TokenService(clockService);
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
    }

    private String signed(final String secret, final JWSAlgorithm algorithm, final JWTClaimsSet claims)
        throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
            .subject("7").claim("email", "joe@example.com").claim("role", "producer")
            .expirationTime(Date.from(NOW.plus(Duration.ofHours(1))));
    }

    private void assertUnavailable(final Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                // Says nothing about why, or what to set.
                assertThat(e.getReason()).isEqualTo("Sign-in is not available right now. Please try again later.");
            });
    }

    @Test
    void an_issued_token_should_verify_and_carry_the_user() {
        TokenService.Claims claims = tokenService.verify(tokenService.issue(user)).orElseThrow();

        assertThat(claims.userId()).isEqualTo(7);
        assertThat(claims.email()).isEqualTo("joe@example.com");
        assertThat(claims.role()).isEqualTo("producer");
    }

    @Test
    void token_should_be_valid_for_seven_days() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(tokenService.issue(user)).getJWTClaimsSet();

        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(claims.getSubject()).isEqualTo("7");
    }

    @Test
    void token_should_stop_working_exactly_when_it_expires() {
        String token = tokenService.issue(user);

        lenient().when(clockService.now()).thenReturn(NOW.plus(Duration.ofDays(7)).minusSeconds(1));
        assertThat(tokenService.verify(token)).isPresent();

        lenient().when(clockService.now()).thenReturn(NOW.plus(Duration.ofDays(7)));
        assertThat(tokenService.verify(token)).isEmpty();
    }

    @Test
    void token_signed_with_a_different_secret_should_be_rejected() throws Exception {
        String forged = signed("another-secret-another-secret-123", JWSAlgorithm.HS256, validClaims().build());

        assertThat(tokenService.verify(forged)).isEmpty();
    }

    @Test
    void token_with_a_tampered_payload_should_be_rejected() {
        String[] parts = tokenService.issue(user).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace("\"sub\":\"7\"", "\"sub\":\"1\"");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThat(forgedPayload).isNotEqualTo(parts[1]);
        assertThat(tokenService.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void an_unsigned_token_should_be_rejected() {
        String unsigned = new PlainJWT(validClaims().build()).serialize();

        assertThat(tokenService.verify(unsigned)).isEmpty();
    }

    @Test
    void token_signed_with_a_different_algorithm_should_be_rejected() throws Exception {
        String hs512 = signed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            JWSAlgorithm.HS512, validClaims().build());

        assertThat(tokenService.verify(hs512)).isEmpty();
    }

    @Test
    void token_with_no_expiry_should_be_rejected() throws Exception {
        String immortal = signed(SECRET, JWSAlgorithm.HS256,
            new JWTClaimsSet.Builder().subject("7").claim("email", "joe@example.com").build());

        assertThat(tokenService.verify(immortal)).isEmpty();
    }

    @Test
    void token_whose_subject_is_not_a_user_id_should_be_rejected() throws Exception {
        String odd = signed(SECRET, JWSAlgorithm.HS256, validClaims().subject("not-a-number").build());

        assertThat(tokenService.verify(odd)).isEmpty();
    }

    @Test
    void anything_that_is_not_a_token_should_be_rejected() {
        assertThat(tokenService.verify("")).isEmpty();
        assertThat(tokenService.verify("not.a.token")).isEmpty();
        assertThat(tokenService.verify("garbage")).isEmpty();
    }

    @Test
    void without_a_secret_nothing_should_be_issued_or_verified() {
        ReflectionTestUtils.setField(tokenService, "secret", "NOT_SET");

        assertUnavailable(() -> tokenService.issue(user));
        assertUnavailable(() -> tokenService.verify("a.b.c"));
        assertUnavailable(() -> tokenService.requireConfigured());
    }

    @Test
    void secret_that_is_too_short_to_be_safe_should_be_treated_as_not_configured() {
        ReflectionTestUtils.setField(tokenService, "secret", "x".repeat(TokenService.MIN_SECRET_BYTES - 1));
        assertUnavailable(() -> tokenService.issue(user));

        ReflectionTestUtils.setField(tokenService, "secret", null);
        assertUnavailable(() -> tokenService.issue(user));
    }

    @Test
    void secret_of_exactly_the_minimum_length_should_work() {
        ReflectionTestUtils.setField(tokenService, "secret", "x".repeat(TokenService.MIN_SECRET_BYTES));

        assertThatCode(() -> tokenService.requireConfigured()).doesNotThrowAnyException();
        assertThat(tokenService.verify(tokenService.issue(user))).isPresent();
    }
}
