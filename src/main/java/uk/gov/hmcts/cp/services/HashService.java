package uk.gov.hmcts.cp.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Service
public class HashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ENCODED_PREFIX = "{";

    private final PasswordEncoder passwordEncoder;
    private final SecretKeySpec pepperKey;

    public HashService(
        final PasswordEncoder passwordEncoder,
        @Value("${marketplace.user-password.pepper:}") final String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException(
                "marketplace.user-password.pepper is not set. It comes from the mounted key vault "
                    + "secret marketplace-USER-PASSWORD-PEPPER (alias USER_PASSWORD_PEPPER). Without it "
                    + "no password can be hashed or verified, so startup fails here rather "
                    + "than at the first sign-in.");
        }
        this.passwordEncoder = passwordEncoder;
        this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String hash(final String rawPassword) {
        return passwordEncoder.encode(peppered(rawPassword));
    }

    public boolean matches(final String rawPassword, final String storedHash) {
        if (rawPassword == null || !wasEncodedByThisService(storedHash)) {
            return false;
        }
        return passwordEncoder.matches(peppered(rawPassword), storedHash);
    }

    private static boolean wasEncodedByThisService(final String storedHash) {
        return storedHash != null && storedHash.startsWith(ENCODED_PREFIX);
    }

    private String peppered(final String rawPassword) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            return Base64.getEncoder()
                .encodeToString(mac.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to apply the password pepper", e);
        }
    }
}
