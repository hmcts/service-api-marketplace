package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.NewSubscriptionKey;
import uk.gov.hmcts.cp.domain.SubscriptionKey;
import uk.gov.hmcts.cp.services.ApimSubscriptionKeyService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Statuses and validation come from the annotations and are covered by
 * ApimSubscriptionKeyIntegrationTest; what is left here is that each endpoint passes the right
 * arguments to the right call.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionKeyControllerTest {

    @Mock
    private ApimSubscriptionKeyService subscriptionKeys;

    @InjectMocks
    private SubscriptionKeyController controller;

    private final SubscriptionKey key =
        new SubscriptionKey("team-alpha", "Team Alpha", "scope", "hearing-results", "active", null, null);

    @Test
    void listing_keys_should_return_what_the_service_found() {
        when(subscriptionKeys.list()).thenReturn(List.of(key));

        assertThat(controller.listKeys()).containsExactly(key);
    }

    @Test
    void creating_a_key_should_pass_the_name_product_and_display_name_through() {
        NewSubscriptionKey request = new NewSubscriptionKey("team-alpha", "hearing-results", "Team Alpha");
        when(subscriptionKeys.create("team-alpha", "hearing-results", "Team Alpha")).thenReturn(key);

        assertThat(controller.createKey(request)).isEqualTo(key);
        verify(subscriptionKeys).create("team-alpha", "hearing-results", "Team Alpha");
    }

    @Test
    void reading_key_values_should_ask_for_that_name_and_nothing_else() {
        SubscriptionKey values = SubscriptionKey.keysOnly("team-alpha", "primary", "secondary");
        when(subscriptionKeys.keyValues("team-alpha")).thenReturn(values);

        assertThat(controller.getKeyValues("team-alpha")).isEqualTo(values);
        // Reading values must not also go and list, which would be a second secret read.
        verify(subscriptionKeys).keyValues("team-alpha");
    }

    @Test
    void deleting_a_key_should_delegate_by_name() {
        controller.deleteKey("team-alpha");

        verify(subscriptionKeys).delete("team-alpha");
    }
}
