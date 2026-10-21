package uk.gov.hmcts.amp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

@Slf4j
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    // Conditional so a test can register a client pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public NotificationClientApi notificationClient(final NotifyProperties config) {
        if (!config.configured()) {
            // Not a startup failure: AAT has no key yet, and a pod that will not boot hangs the
            // whole deploy. NotifyService reports it per send instead.
            log.warn("NOTIFY_API_KEY is not set: no email will be sent from this environment.");
            return null;
        }
        log.info("Notify is configured, sending through {}", config.baseUrl());
        return new NotificationClient(config.apiKey(), config.baseUrl());
    }
}
