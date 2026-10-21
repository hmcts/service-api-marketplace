package uk.gov.hmcts.amp.config;

import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;
import uk.gov.hmcts.amp.services.EntraAppRegistrationService;
import uk.gov.hmcts.amp.services.LocalApimSubscriptionKeyService;
import uk.gov.hmcts.amp.services.LocalEntraAppRegistrationService;
import uk.gov.hmcts.amp.services.UserService;

/**
 * ENTRA_BYPASS=true, for running locally without the onboarding secret or an Azure login: Entra and
 * API Management are replaced by in-memory stand-ins that make up client ids, secrets and keys.
 * Never set it in a chart. Nothing it issues works against the real gateway.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "local-bypass.enabled", havingValue = "true")
public class LocalBypassConfig {

    @Bean
    @Primary
    public EntraAppRegistrationService localEntraAppRegistrationService(final GraphServiceClient graph,
                                                                        final EntraProperties config) {
        log.warn("ENTRA_BYPASS is on: Entra application registration is faked in memory. Local use only.");
        return new LocalEntraAppRegistrationService(graph, config);
    }

    @Bean
    @Primary
    public ApimSubscriptionKeyService localApimSubscriptionKeyService(final ApiManagementManager apim,
                                                                      final UserService users,
                                                                      final ApimProperties apimProperties) {
        log.warn("ENTRA_BYPASS is on: APIM subscription keys are faked in memory. Local use only.");
        return new LocalApimSubscriptionKeyService(apim, users, apimProperties);
    }
}
