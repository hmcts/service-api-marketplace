package uk.gov.hmcts.amp.config;

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

    public static final String UNSET = "00000000-0000-0000-0000-000000000000";

    @Value("${apim.tenant-id}")
    private String tenantId;

    @Value("${apim.subscription-id}")
    private String azureSubscriptionId;

    // Conditional so a test can register a manager pointed at WireMock instead.
    @Bean
    @ConditionalOnMissingBean
    public ApiManagementManager apiManagementManager() {
        log.info("APIM tenant {}, subscription {}", tenantId, azureSubscriptionId);
        // Refuse to start rather than warn. Zeros here means every subscription call comes back
        // as an unreachable upstream, which sends you looking at Azure instead of at the
        // deployment. Every environment that runs this has to set it, AAT and preview included.
        if (azureSubscriptionId == null || azureSubscriptionId.isBlank()
            || UNSET.equals(azureSubscriptionId)) {
            throw new IllegalStateException(
                "APIM_SUBSCRIPTION_ID is not set. It is the Azure subscription holding the API "
                    + "Management instance, not an APIM subscription key.");
        }
        return ApiManagementManager.authenticate(
            new DefaultAzureCredentialBuilder().build(),
            new AzureProfile(tenantId, azureSubscriptionId, AzureEnvironment.AZURE));
    }
}
