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
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.services.SubscriptionService;
import uk.gov.hmcts.cp.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UserService userService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private static final int USER_ID = 1;
    private static final SubscriptionRequest REQUEST = SubscriptionRequest.builder().build();
    private static final UserEntity USER = UserEntity.builder().build();

    @Test
    void submitting_a_valid_request_should_return_201_with_subscription_response() {
        SubscriptionResponse serviceResponse = SubscriptionResponse.builder().build();
        when(userService.validateUser(USER_ID)).thenReturn(USER);
        when(subscriptionService.submit(USER, REQUEST)).thenReturn(serviceResponse);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.submit(USER_ID, REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
    }
}
