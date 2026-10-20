package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.amp.domain.ApiSubscriptionRequest;
import uk.gov.hmcts.amp.domain.ApiSubscriptionResponse;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;
import uk.gov.hmcts.amp.services.ApplicationService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionKeyControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private ApimSubscriptionKeyService subscriptionKeys;

    @Mock
    private ApplicationService applications;

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
    void creating_a_key_should_pass_the_application_and_short_code_through() {
        ApiSubscriptionRequest request = new ApiSubscriptionRequest(CLIENT_ID, "slc");
        ApiSubscriptionResponse subscribed = new ApiSubscriptionResponse("slc", "Court Schedule",
            "cp-crime-schedulingandlisting", "amp-user001-case-viewer-slc", "a-key");
        when(applications.subscribe(OID, CLIENT_ID, "slc")).thenReturn(subscribed);

        assertThat(controller.createKey(OID, request)).isEqualTo(subscribed);
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
    void deleting_a_key_should_remove_the_subscription_and_the_row_that_recorded_it() {
        controller.deleteKey(OID, "team-alpha");

        verify(subscriptionKeys).delete(OID, "team-alpha");
        verify(applications).forgetApi("team-alpha");
    }
}
