package uk.gov.hmcts.amp.notify;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class EmailMask {

    private EmailMask() {
    }

    public static String masked(final String emailAddress) {
        final int at = emailAddress == null ? -1 : emailAddress.indexOf('@');
        if (at < 1) {
            return "***";
        }
        final String initials = Arrays.stream(emailAddress.substring(0, at).split("\\."))
            .filter(part -> !part.isBlank())
            .map(part -> part.substring(0, 1))
            .collect(Collectors.joining("."));
        return initials.isEmpty() ? "***" : initials + emailAddress.substring(at);
    }
}
