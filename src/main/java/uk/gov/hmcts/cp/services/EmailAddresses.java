package uk.gov.hmcts.cp.services;

/**
 * Whether a string looks like an email address. This is about catching typos, not proving an address
 * exists: something@something.something, no spaces, no empty parts.
 *
 * <p>This runs on input from anyone, signed in or not, so it is written as a single pass over the
 * characters rather than as a regular expression. The length is checked first, so a huge string is
 * turned away without being read at all, and there is no pattern to backtrack or to overflow the stack
 * on a long input (CodeQL flagged the first regex form of this for backtracking; Sonar flagged the
 * second for the stack).
 */
public final class EmailAddresses {

    /** The longest an address may be (RFC 5321's limit for a path, less the brackets). */
    public static final int MAX_LENGTH = 254;

    private static final char AT = '@';
    private static final char DOT = '.';

    private EmailAddresses() {
    }

    public static boolean isValid(final String email) {
        if (email == null || email.length() > MAX_LENGTH) {
            return false;
        }
        int at = email.indexOf(AT);
        // Something before the @, and only one @.
        if (at < 1 || email.indexOf(AT, at + 1) >= 0) {
            return false;
        }
        return hasNoWhitespace(email) && hasAtLeastTwoDomainLabels(email, at + 1);
    }

    // The characters a regular expression's \s matches by default, so that behaviour is unchanged.
    private static boolean hasNoWhitespace(final String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r') {
                return false;
            }
        }
        return true;
    }

    // The domain is labels separated by dots: at least two, none empty (so no leading, trailing or
    // doubled dot).
    private static boolean hasAtLeastTwoDomainLabels(final String email, final int domainStart) {
        int labels = 0;
        int labelLength = 0;
        for (int i = domainStart; i < email.length(); i++) {
            if (email.charAt(i) == DOT) {
                if (labelLength == 0) {
                    return false;
                }
                labels++;
                labelLength = 0;
            } else {
                labelLength++;
            }
        }
        return labelLength > 0 && labels >= 1;
    }
}
