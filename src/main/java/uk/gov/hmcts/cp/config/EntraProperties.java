package uk.gov.hmcts.cp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entra")
public record EntraProperties(String tenantId, String onboardingClientId, String onboardingClientSecret,
                              String apiClientId, int maxAttempts, int retryDelayMillis,
                              int assignmentAttempts, int assignmentDelayMillis) {

    public boolean credentialConfigured() {
        return onboardingClientSecret != null && !onboardingClientSecret.isBlank()
            && !"NOT_SET".equals(onboardingClientSecret);
    }
}
