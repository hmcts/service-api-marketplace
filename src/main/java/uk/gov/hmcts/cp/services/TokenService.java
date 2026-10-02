package uk.gov.hmcts.cp.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.UserEntity;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * The signed bearer token the frontend keeps in localStorage and sends as Authorization: Bearer.
 * HS256, seven days, carrying the user's id, email and role - the same token amp-auth issued, so a
 * client cannot tell which of the two it is talking to.
 *
 * <p>The signing secret is JWT_SECRET. Like the credentials in the Entra/APIM clients, there is no
 * default and no mock: without a real secret of at least 32 bytes nothing can be issued or
 * verified, and the endpoints answer 503 rather than minting tokens anyone could forge.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    static final Duration VALIDITY = Duration.ofDays(7);
    static final int MIN_SECRET_BYTES = 32;
    private static final String NOT_SET = "NOT_SET";

    private final ClockService clockService;

    @Value("${JWT_SECRET:NOT_SET}")
    private String secret;

    public record Claims(int userId, String email, String role) {
    }

    public String issue(final UserEntity user) {
        byte[] key = signingKey();
        Instant now = clockService.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(String.valueOf(user.getId()))
            .claim("email", user.getEmail())
            .claim("role", user.getRole())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(VALIDITY)))
            .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("Could not sign a token", e);
            throw unavailable();
        }
    }

    /** Empty for anything that is not a currently valid token we signed: malformed, forged, or expired. */
    public Optional<Claims> verify(final String token) {
        byte[] key = signingKey();
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (expiry == null || !expiry.toInstant().isAfter(clockService.now())) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                Integer.parseInt(claims.getSubject()),
                claims.getStringClaim("email"),
                claims.getStringClaim("role")));
        } catch (ParseException | JOSEException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void requireConfigured() {
        signingKey();
    }

    private byte[] signingKey() {
        byte[] key = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (NOT_SET.equals(secret) || key.length < MIN_SECRET_BYTES) {
            // The caller is told nothing about why; the reason belongs in the log.
            log.error("JWT_SECRET is not set to a value of at least {} bytes, so sign-in is unavailable",
                MIN_SECRET_BYTES);
            throw unavailable();
        }
        return key;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Sign-in is not available right now. Please try again later.");
    }
}
