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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ApimProperties.class)
public class ApimConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final ApimProperties apimProperties;

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

    @Bean
    @ConditionalOnMissingBean
    public RestClient developerPortal() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(apimProperties.portalUrl()).requestFactory(factory).build();
    }
}
