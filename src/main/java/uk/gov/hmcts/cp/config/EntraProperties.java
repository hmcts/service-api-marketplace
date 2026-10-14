package uk.gov.hmcts.cp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "entra")
public record EntraProperties(String tenantId, String onboardingClientId,

    @NotBlank(message = "set ENTRA_ONBOARDING_CLIENT_SECRET - the application registration "
        + "endpoints cannot work without it, and failing here says so once rather than on every call")
    String onboardingClientSecret,

    String apiClientId, int maxAttempts, int retryDelayMillis) {

    public boolean credentialConfigured() {
        return isSet(onboardingClientId) && isSet(onboardingClientSecret);
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank() && !"NOT_SET".equals(value);
    }
}
