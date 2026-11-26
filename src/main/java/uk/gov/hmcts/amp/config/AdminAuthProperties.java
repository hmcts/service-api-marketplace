package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin.auth")
public record AdminAuthProperties(String tenantId, String audience, String role,
                                  String issuerOverride, String jwkSetUriOverride) {

    private static final String DEFAULT_ROLE = "amp_admin";

    public boolean usable() {
        return isSet(tenantId) && isSet(audience);
    }

    /** The issuer is the directory id as a host, not the friendly name its endpoints answer on. */
    public String issuer() {
        if (isSet(issuerOverride)) {
            return issuerOverride;
        }
        return "https://%s.ciamlogin.com/%s/v2.0".formatted(tenantId, tenantId);
    }

    public String jwkSetUri() {
        if (isSet(jwkSetUriOverride)) {
            return jwkSetUriOverride;
        }
        return "https://%s.ciamlogin.com/%s/discovery/v2.0/keys".formatted(tenantId, tenantId);
    }

    public String roleOrDefault() {
        return isSet(role) ? role : DEFAULT_ROLE;
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank();
    }
}
