package uk.gov.hmcts.cp.config;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ApimConfig {

    private static final String UNSET = "00000000-0000-0000-0000-000000000000";

    @Value("${apim.tenant-id}")
    private String tenantId;

    @Value("${apim.subscription-id}")
    private String azureSubscriptionId;

    // Conditional so a test can register a manager pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public ApiManagementManager apiManagementManager() {
        log.info("APIM tenant {}, subscription {}", tenantId, azureSubscriptionId);
        if (UNSET.equals(azureSubscriptionId)) {
            log.warn("APIM_SUBSCRIPTION_ID is not set: the subscription key endpoints will fail.");
        }
        return ApiManagementManager.authenticate(
            new DefaultAzureCredentialBuilder().build(),
            new AzureProfile(tenantId, azureSubscriptionId, AzureEnvironment.AZURE));
    }
}
