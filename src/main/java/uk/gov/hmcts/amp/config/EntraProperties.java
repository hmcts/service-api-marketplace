package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@ConfigurationProperties(prefix = "entra")
public record EntraProperties(String tenantId, String onboardingClientId, String onboardingClientSecret,
                              Map<String, String> resourceAppIds, int maxAttempts, int retryDelayMillis) {

    public String resourceAppId(final String shortCode) {
        final String appId = resourceAppIds == null ? null : resourceAppIds.get(shortCode);
        if (appId == null || appId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No Entra resource app is configured for " + shortCode + " in this environment.");
        }
        return appId;
    }

    public boolean credentialConfigured() {
        return isSet(onboardingClientId) && isSet(onboardingClientSecret);
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank() && !"NOT_SET".equals(value);
    }
}
