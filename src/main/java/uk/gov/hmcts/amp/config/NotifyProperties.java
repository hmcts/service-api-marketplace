package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(String apiKey, String baseUrl, List<String> reviewInboxes) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<String> reviewers() {
        return reviewInboxes == null ? List.of()
            : reviewInboxes.stream().map(String::trim).filter(inbox -> !inbox.isEmpty()).toList();
    }
}
