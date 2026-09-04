package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.SubscriptionService;
import uk.gov.hmcts.cp.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UserService userService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private final SubscriptionRequest subscriptionRequest = SubscriptionRequest.builder().build();
    private final UserResponse userResponse = UserResponse.builder().id(1).build();
    private final SubscriptionResponse subscriptionResponse = SubscriptionResponse.builder().build();

    @Test
    void submitting_a_valid_request_should_return_201_with_subscription_response() {
        SubscriptionResponse serviceResponse = SubscriptionResponse.builder().build();
        when(userService.validateUser(1)).thenReturn(userResponse);
        when(subscriptionService.submit(userResponse.getId(), subscriptionRequest)).thenReturn(subscriptionResponse);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.submit(1, subscriptionRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
    }

    @Test
    void deleting_a_subscription_should_return_204() {
        String reference = "AR-2026-414D8U";

        ResponseEntity<Void> response = subscriptionController.delete(reference);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(subscriptionService).delete(reference);
    }
}
