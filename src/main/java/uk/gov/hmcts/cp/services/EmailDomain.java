package uk.gov.hmcts.cp.services;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Reads the domain from an email address, and says whether a domain could belong to an organisation. */
public final class EmailDomain {

    /** Mail providers anyone can sign up to; no organisation can own one. */
    static final Set<String> SHARED = Set.of("gmail.com", "googlemail.com", "outlook.com", "hotmail.com",
        "hotmail.co.uk", "live.com", "yahoo.com", "yahoo.co.uk", "icloud.com", "me.com", "aol.com", "proton.me",
        "protonmail.com");

    private EmailDomain() {
    }

    /**
     * The lower-case domain of an address with exactly one {@code @}, or empty if it does not have one.
     */
    public static Optional<String> of(final String email) {
        if (email == null) {
            return Optional.empty();
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at < 1 || at != trimmed.lastIndexOf('@')) {
            return Optional.empty();
        }
        return normalise(trimmed.substring(at + 1));
    }

    /**
     * A domain as it is stored: trimmed and lower case, with at least one dot and nothing that is not a name.
     */
    public static Optional<String> normalise(final String domain) {
        if (domain == null) {
            return Optional.empty();
        }
        String value = domain.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 3 || value.length() > 253 || value.indexOf('.') < 1 || value.startsWith(".")
            || value.endsWith(".") || value.length() - value.lastIndexOf('.') - 1 < 2
            || !isLettersDigitsDotsAndHyphens(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static boolean isShared(final String domain) {
        return SHARED.contains(domain);
    }

    private static boolean isLettersDigitsDotsAndHyphens(final String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
