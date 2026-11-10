package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "apim")
public record ApimProperties(String tenantId, String subscriptionId, String resourceGroup,
                             String serviceName, String portalUrl, Map<String, String> tryItNowApis) {

    private static final String UNSET = "00000000-0000-0000-0000-000000000000";

    public boolean credentialConfigured() {
        return subscriptionId != null && !subscriptionId.isBlank() && !UNSET.equals(subscriptionId);
    }

    /** The Try It Now API's name in APIM for a short code, or null where none is configured. */
    public String tryItNowApi(final String shortCode) {
        final String name = tryItNowApis == null ? null : tryItNowApis.get(shortCode);
        return name == null || name.isBlank() ? null : name;
    }
}