package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apim")
public record ApimProperties(String tenantId, String subscriptionId, String resourceGroup,
                             String serviceName) {

    private static final String UNSET = "00000000-0000-0000-0000-000000000000";

    public boolean credentialConfigured() {
        return subscriptionId != null && !subscriptionId.isBlank() && !UNSET.equals(subscriptionId);
    }
}
