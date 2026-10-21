package uk.gov.hmcts.amp.config;

import org.junit.jupiter.api.Test;
import uk.gov.service.notify.NotificationClient;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyConfigTest {

    // Notify parses the service id and secret out of the key by position, so a stand-in still
    // has to be shaped like a real one.
    private static final String KEY = "amptest-00000000-0000-0000-0000-000000000000"
        + "-11111111-1111-1111-1111-111111111111";

    private final NotifyConfig config = new NotifyConfig();

    @Test
    void an_api_key_should_give_a_client_pointed_at_the_configured_notify() {
        assertThat(config.notificationClient(new NotifyProperties(KEY, "https://notify.example")))
            .isInstanceOf(NotificationClient.class);
    }

    @Test
    void no_key_should_give_no_client_rather_than_stopping_the_application() {
        assertThat(config.notificationClient(new NotifyProperties("", "https://notify.example")))
            .isNull();
    }

    @Test
    void null_key_should_be_treated_the_same_as_a_blank_one() {
        assertThat(new NotifyProperties(null, "https://notify.example").configured()).isFalse();
    }
}
