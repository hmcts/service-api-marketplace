package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where an admin token comes from, and which app role it has to carry. An environment that
 * supplies neither a tenant nor an audience has no Entra sign-in, so the gate stays open there
 * and the admin endpoints keep to the role check they have always made against the database.
 */
@ConfigurationProperties(prefix = "admin.auth")
public record AdminAuthProperties(String tenantId, String audience, String role) {

    private static final String DEFAULT_ROLE = "amp_admin";

    public boolean usable() {
        return isSet(tenantId) && isSet(audience);
    }

    /** The issuer is the directory id as a host, not the friendly name its endpoints answer on. */
    public String issuer() {
        return "https://%s.ciamlogin.com/%s/v2.0".formatted(tenantId, tenantId);
    }

    public String roleOrDefault() {
        return isSet(role) ? role : DEFAULT_ROLE;
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank();
    }
}
