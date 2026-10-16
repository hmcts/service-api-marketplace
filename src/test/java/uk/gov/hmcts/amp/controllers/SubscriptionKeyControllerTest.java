package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.amp.domain.NewSubscriptionKey;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionKeyControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

    @Mock
    private ApimSubscriptionKeyService subscriptionKeys;

    @InjectMocks
    private SubscriptionKeyController controller;

    private final SubscriptionKey key =
        new SubscriptionKey("team-alpha", "Team Alpha", "scope", "hearing-results", "active", null, null);

    @Test
    void listing_keys_should_return_what_the_service_found() {
        when(subscriptionKeys.list(OID)).thenReturn(List.of(key));

        assertThat(controller.listKeys(OID)).containsExactly(key);
    }

    @Test
    void creating_a_key_should_pass_the_name_product_and_display_name_through() {
        NewSubscriptionKey request = new NewSubscriptionKey("team-alpha", "hearing-results", "Team Alpha");
        when(subscriptionKeys.create(OID, "team-alpha", "hearing-results", "Team Alpha")).thenReturn(key);

        assertThat(controller.createKey(OID, request)).isEqualTo(key);
        verify(subscriptionKeys).create(OID, "team-alpha", "hearing-results", "Team Alpha");
    }

    @Test
    void reading_key_values_should_ask_for_that_name_and_nothing_else() {
        SubscriptionKey values = SubscriptionKey.keysOnly("team-alpha", "primary", "secondary");
        when(subscriptionKeys.keyValues(OID, "team-alpha")).thenReturn(values);

        assertThat(controller.getKeyValues(OID, "team-alpha")).isEqualTo(values);
        // Reading values must not also go and list, which would be a second secret read.
        verify(subscriptionKeys).keyValues(OID, "team-alpha");
    }

    @Test
    void deleting_a_key_should_delegate_by_name() {
        controller.deleteKey(OID, "team-alpha");

        verify(subscriptionKeys).delete(OID, "team-alpha");
    }
}
