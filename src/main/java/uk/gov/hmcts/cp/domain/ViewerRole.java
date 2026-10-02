package uk.gov.hmcts.cp.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * What a caller may do on one application, lowest to highest. The owner is whoever created it and
 * always outranks the two team roles; a developer can work with an application, an administrator
 * can also change it, manage its secrets and its team, and only the owner can delete it.
 */
public enum ViewerRole {
    DEVELOPER, ADMINISTRATOR, OWNER;

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean atLeast(final ViewerRole minimum) {
        return compareTo(minimum) >= 0;
    }

    /** The role stored for a team member, or empty for anything that is not a team role. */
    public static Optional<ViewerRole> teamRole(final String stored) {
        if ("developer".equals(stored)) {
            return Optional.of(DEVELOPER);
        }
        if ("administrator".equals(stored)) {
            return Optional.of(ADMINISTRATOR);
        }
        return Optional.empty();
    }
}
