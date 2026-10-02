package uk.gov.hmcts.cp.services;

import java.util.regex.Pattern;

/**
 * Whether a string looks like an email address. This is about catching typos, not proving an address
 * exists: something@something.something, no spaces, no empty parts.
 *
 * <p>Two things here are for safety, because this runs on input from anyone, signed in or not. The
 * length is checked before the pattern is, so a huge string is turned away without being matched at
 * all; and the pattern keeps the dots between the labels of the domain out of the labels themselves,
 * so there is only one way to match any string and a crafted one cannot make the matcher backtrack
 * (CodeQL flagged the earlier form of this, where {@code [^\s@]+\.[^\s@]+} let the dot match either way).
 */
public final class EmailAddresses {

    /** The longest an address may be (RFC 5321's limit for a path, less the brackets). */
    public static final int MAX_LENGTH = 254;

    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");

    private EmailAddresses() {
    }

    public static boolean isValid(final String email) {
        return email != null && email.length() <= MAX_LENGTH && PATTERN.matcher(email).matches();
    }
}
