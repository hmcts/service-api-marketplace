package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;
import uk.gov.hmcts.amp.services.EntraAppRegistrationService;

import static org.assertj.core.api.Assertions.assertThat;

/** The bypass is off unless asked for, so nothing deployed can end up issuing made-up credentials. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class LocalBypassOffIntegrationTest {

    @Autowired
    private EntraAppRegistrationService registrations;

    @Autowired
    private ApimSubscriptionKeyService subscriptionKeys;

    @Test
    void by_default_the_real_entra_and_apim_services_should_be_used() {
        assertThat(registrations).isExactlyInstanceOf(EntraAppRegistrationService.class);
        assertThat(subscriptionKeys).isExactlyInstanceOf(ApimSubscriptionKeyService.class);
    }
}
