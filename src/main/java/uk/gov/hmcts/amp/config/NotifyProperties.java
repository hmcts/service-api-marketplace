package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(String apiKey, String baseUrl) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
