package uk.gov.hmcts.amp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    private final NotifyProperties notifyProperties;

    // Conditional so a test can register a client pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public NotificationClientApi notificationClient() {
        if (!notifyProperties.configured()) {
            log.warn("NOTIFY_API_KEY is not set: no email will be sent from this environment.");
            return null;
        }
        return new NotificationClient(notifyProperties.apiKey(), notifyProperties.baseUrl());
    }
}
