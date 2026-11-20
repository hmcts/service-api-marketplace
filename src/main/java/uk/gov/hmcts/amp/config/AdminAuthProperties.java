package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where an admin token comes from, and which app role it has to carry.
 *
 * <p>Live and non-live are different Entra directories, so every value here belongs to one
 * environment: the issuer, the audience and the people holding the role all differ, and nothing is
 * defaulted to a tenant. Only {@code role} has a default, because the string has to be identical
 * everywhere for the same code to recognise it.
 *
 * <p>Unset means closed, not open: {@link AdminSecurityConfig} refuses every {@code /admin/**}
 * request when this is not configured.
 */
@ConfigurationProperties(prefix = "admin.auth")
public record AdminAuthProperties(boolean enabled, String issuer, String audience, String role) {

    private static final String DEFAULT_ROLE = "amp_admin";

    public boolean usable() {
        return enabled && isSet(issuer) && isSet(audience);
    }

    public String roleOrDefault() {
        return isSet(role) ? role : DEFAULT_ROLE;
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank();
    }
}
