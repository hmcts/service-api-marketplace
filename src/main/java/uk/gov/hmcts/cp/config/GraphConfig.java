package uk.gov.hmcts.cp.config;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EntraProperties.class)
public class GraphConfig {

    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    // Conditional so a test can register a client pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public GraphServiceClient graphServiceClient(final EntraProperties config) {
        return new GraphServiceClient(new ClientSecretCredentialBuilder()
            .tenantId(config.tenantId())
            .clientId(config.onboardingClientId())
            .clientSecret(config.onboardingClientSecret())
            .build(), GRAPH_SCOPE);
    }
}
