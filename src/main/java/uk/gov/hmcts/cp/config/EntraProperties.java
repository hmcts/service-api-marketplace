package uk.gov.hmcts.cp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entra")
public record EntraProperties(String tenantId, String onboardingClientId, String onboardingClientSecret,
                              String apiClientId, int maxAttempts, int retryDelayMillis) {

    public boolean credentialConfigured() {
        return isSet(onboardingClientId) && isSet(onboardingClientSecret);
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank() && !"NOT_SET".equals(value);
    }
}
