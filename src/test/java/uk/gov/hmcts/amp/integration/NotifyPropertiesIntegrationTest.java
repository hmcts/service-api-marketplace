package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.config.NotifyProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** The defaults in application.yaml, which nothing else reads unmocked. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class NotifyPropertiesIntegrationTest {

    @Autowired
    private NotifyProperties notifyProperties;

    @Test
    void the_reviewers_should_default_to_the_marketplace_mailbox_alone() {
        // A typo here sends nowhere and says nothing, so the default is pinned rather than trusted.
        // Production widens this to the panel and the asset owner through the helm values.
        assertThat(notifyProperties.reviewers()).containsExactly("APIMarketplace@hmcts.net");
    }

    @Test
    void no_api_key_should_leave_notify_unconfigured_rather_than_failing_startup() {
        assertThat(notifyProperties.configured()).isFalse();
    }
}
