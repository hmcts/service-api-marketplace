package uk.gov.hmcts.cp.services;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * bcrypt only, with no pepper: the pepper tried in AMP-1102 needed a Terraform-managed secret that
 * the AAT infrastructure whitelist rejected (AMP-1103), and the account service this replaces
 * (amp-auth) never had one either. Uses spring-security-crypto directly rather than Spring
 * Security, whose default filter chain would lock every existing endpoint.
 */
@Service
public class PasswordService {

    static final int STRENGTH = 12;

    /** bcrypt only reads the first 72 bytes, and newer versions refuse to hash more than that. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    // A real hash of something nobody knows, so that checking a password for an account that does
    // not exist takes as long as checking a wrong one, and the two cannot be told apart by timing.
    private final String decoyHash = encoder.encode("decoy-" + UUID.randomUUID());

    public String hash(final String password) {
        return encoder.encode(password);
    }

    public boolean matches(final String password, final String hash) {
        if (isTooLong(password)) {
            return false;
        }
        return encoder.matches(password, hash);
    }

    public void spendTimeAsIfChecking(final String password) {
        matches(password, decoyHash);
    }

    public static boolean isTooLong(final String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
