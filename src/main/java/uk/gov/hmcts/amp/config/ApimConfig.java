package uk.gov.hmcts.amp.config;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ApimProperties.class)
public class ApimConfig {

    private final ApimProperties apimProperties;

    // Conditional so a test can register a manager pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public ApiManagementManager apiManagementManager() {
        if (!apimProperties.credentialConfigured()) {
            log.warn("APIM_SUBSCRIPTION_ID is not set: the subscription key endpoints will fail.");
        }
        return ApiManagementManager.authenticate(
            new DefaultAzureCredentialBuilder().build(),
            new AzureProfile(apimProperties.tenantId(), apimProperties.subscriptionId(),
                AzureEnvironment.AZURE));
    }
}
