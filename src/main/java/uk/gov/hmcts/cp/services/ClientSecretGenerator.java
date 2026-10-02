package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Makes the client secret shown to a developer, once, when an application or a replacement secret
 * is created: "amp_" and 48 hex characters from a secure random source, the same form amp-auth
 * issued. Short enough to stay inside bcrypt's 72-byte limit, which is what it is stored as.
 */
@Service
public class ClientSecretGenerator {

    static final String PREFIX = "amp_";
    static final int RANDOM_BYTES = 24;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    /** The last four characters: all the UI can show of a secret once it has been shown. */
    public static String previewOf(final String secret) {
        return secret.substring(secret.length() - 4);
    }
}
