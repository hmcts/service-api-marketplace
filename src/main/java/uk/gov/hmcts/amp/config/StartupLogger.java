package uk.gov.hmcts.amp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.amp.notify.EmailMask;

import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupLogger {

    private final Environment environment;
    private final ApimProperties apimProperties;
    private final EntraProperties entraProperties;
    private final NotifyProperties notifyProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        log.info("Database: {}", databaseSettings());
        log.info("API Management: {}", apimSettings());
        log.info("Entra: {}", entraSettings());
        log.info("Notify: {}", notifySettings());
    }

    String databaseSettings() {
        return "%s as %s, password %s".formatted(
            environment.getProperty("spring.datasource.url"),
            environment.getProperty("spring.datasource.username"),
            whetherSet(environment.getProperty("spring.datasource.password")));
    }

    String apimSettings() {
        return "%s in %s, subscription %s, tenant %s".formatted(
            apimProperties.serviceName(), apimProperties.resourceGroup(),
            apimProperties.subscriptionId(), apimProperties.tenantId());
    }

    String entraSettings() {
        return "tenant %s, onboarding %s, onboarding secret %s, resource apps %s".formatted(
            entraProperties.tenantId(), entraProperties.onboardingClientId(),
            whetherSet(entraProperties.onboardingClientSecret()), resourceApps());
    }

    String notifySettings() {
        return "api key %s, base url %s, production requests reviewed by %s".formatted(
            whetherSet(notifyProperties.apiKey()), notifyProperties.baseUrl(),
            notifyProperties.reviewers().stream().map(EmailMask::masked).toList());
    }

    private String resourceApps() {
        Map<String, String> configured = entraProperties.resourceAppIds();
        return configured == null ? "{}" : new TreeMap<>(configured).toString();
    }

    private static String whetherSet(final String secret) {
        return secret == null || secret.isBlank() || "NOT_SET".equals(secret) ? "NOT SET" : "set";
    }
}
