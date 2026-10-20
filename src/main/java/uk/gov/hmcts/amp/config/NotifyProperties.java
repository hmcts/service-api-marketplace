package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(String apiKey, String baseUrl, String reviewInbox) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasReviewInbox() {
        return reviewInbox != null && !reviewInbox.isBlank();
    }
}
