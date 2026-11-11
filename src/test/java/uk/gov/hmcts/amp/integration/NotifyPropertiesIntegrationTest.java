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
    void each_inbox_should_be_the_one_address_production_sends_to() {
        assertThat(notifyProperties.reviewers()).containsExactly("DataAccessPanel@justice.gov.uk");
        assertThat(notifyProperties.ampTeam()).containsExactly("APIMarketplace@hmcts.net");
    }

    @Test
    void an_environment_that_does_not_name_itself_should_email_the_requester_instead() {
        assertThat(notifyProperties.redirectingToRequester()).isTrue();
    }

    @Test
    void no_api_key_should_leave_notify_unconfigured_rather_than_failing_startup() {
        assertThat(notifyProperties.configured()).isFalse();
    }
}
