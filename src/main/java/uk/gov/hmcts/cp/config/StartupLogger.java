package uk.gov.hmcts.cp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {

    private static final String UNSET = "NOT_SET";
    private static final int HINT_CHARACTERS = 3;
    private static final int SHORTEST_SECRET_WORTH_HINTING_AT = 16;

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    @Value("${POSTGRES_HOST:NOT_SET}")
    private String postgresHost;

    @Value("${POSTGRES_PORT:NOT_SET}")
    private String postgresPort;

    @Value("${POSTGRES_DATABASE:NOT_SET}")
    private String postgresDatabase;

    @Value("${POSTGRES_USER:NOT_SET}")
    private String postgresUser;

    @Value("${POSTGRES_PASS:NOT_SET}")
    private String postgresPass;

    @Value("${JWT_SECRET:NOT_SET}")
    private String jwtSecret;

    @Value("${AZURE_TENANT_ID:NOT_SET}")
    private String azureTenantId;

    @Value("${AZURE_CLIENT_ID:NOT_SET}")
    private String azureClientId;

    @Value("${AZURE_FEDERATED_TOKEN_FILE:NOT_SET}")
    private String azureFederatedTokenFile;

    @Value("${APIM_SUBSCRIPTION_ID:NOT_SET}")
    private String apimSubscriptionId;

    @Value("${APIM_RESOURCE_GROUP:NOT_SET}")
    private String apimResourceGroup;

    @Value("${APIM_SERVICE_NAME:NOT_SET}")
    private String apimServiceName;

    @Value("${ENTRA_TENANT_ID:NOT_SET}")
    private String entraTenantId;

    @Value("${ENTRA_ONBOARDING_CLIENT_ID:NOT_SET}")
    private String entraOnboardingClientId;

    @Value("${ENTRA_ONBOARDING_CLIENT_SECRET:NOT_SET}")
    private String entraOnboardingClientSecret;

    @Value("${ENTRA_API_CLIENT_ID:NOT_SET}")
    private String entraApiClientId;

    @EventListener(ApplicationReadyEvent.class)
    public void logEnvironment() {
        log.info("=== Vault secret diagnostics ===");
        log.info("POSTGRES_HOST                  : {}", postgresHost);
        log.info("POSTGRES_PORT                  : {}", postgresPort);
        log.info("POSTGRES_DATABASE              : {}", postgresDatabase);
        log.info("POSTGRES_USER                  : {}", postgresUser);
        log.info("POSTGRES_PASS                  : {}", maskedSecret(postgresPass));
        log.info("JWT_SECRET                     : {}", maskedSecret(jwtSecret));

        log.info("=== Workload identity (APIM) ===");
        log.info("AZURE_TENANT_ID                : {}", azureTenantId);
        log.info("AZURE_CLIENT_ID                : {}", azureClientId);
        log.info("AZURE_FEDERATED_TOKEN_FILE     : {}", azureFederatedTokenFile);
        log.info("APIM_SUBSCRIPTION_ID           : {}", apimSubscriptionId);
        log.info("APIM_RESOURCE_GROUP            : {}", apimResourceGroup);
        log.info("APIM_SERVICE_NAME              : {}", apimServiceName);

        // Deliberately a different tenant from AZURE_TENANT_ID: the external CIAM one, not the cluster's.
        log.info("=== Entra app registration ===");
        log.info("ENTRA_TENANT_ID                : {}", entraTenantId);
        log.info("ENTRA_ONBOARDING_CLIENT_ID     : {}", entraOnboardingClientId);
        log.info("ENTRA_ONBOARDING_CLIENT_SECRET : {}", maskedSecret(entraOnboardingClientSecret));
        log.info("ENTRA_API_CLIENT_ID            : {}", entraApiClientId);
        log.info("================================");

        if (UNSET.equals(entraOnboardingClientId) || UNSET.equals(entraOnboardingClientSecret)) {
            log.warn("ENTRA_ONBOARDING_CLIENT_ID and _SECRET are both needed: /applications-new will answer 503.");
        }
        if (UNSET.equals(apimSubscriptionId)) {
            log.warn("APIM_SUBSCRIPTION_ID is not set: the subscription key endpoints will fail.");
        }
    }

    private String maskedSecret(final String secret) {
        if (UNSET.equals(secret) || secret.isBlank()) {
            return UNSET;
        }
        if (secret.length() < SHORTEST_SECRET_WORTH_HINTING_AT) {
            return "***SET*** (%d chars)".formatted(secret.length());
        }
        return "%s... (%d chars)".formatted(secret.substring(0, HINT_CHARACTERS), secret.length());
    }
}
