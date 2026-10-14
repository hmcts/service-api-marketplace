package uk.gov.hmcts.cp.config;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApimConfig {

    @Value("${apim.tenant-id}")
    private String tenantId;

    @Value("${apim.subscription-id}")
    private String azureSubscriptionId;

    // Conditional so a test can register a manager pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public ApiManagementManager apiManagementManager() {
        return ApiManagementManager.authenticate(
            new DefaultAzureCredentialBuilder().build(),
            new AzureProfile(tenantId, azureSubscriptionId, AzureEnvironment.AZURE));
    }
}
