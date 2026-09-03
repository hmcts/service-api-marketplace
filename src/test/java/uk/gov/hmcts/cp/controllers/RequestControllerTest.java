package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import uk.gov.hmcts.cp.domain.RequestSummaryResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.RequestService;
import uk.gov.hmcts.cp.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestControllerTest {

    @Mock
    private RequestService requestService;

    @Mock
    private UserService userService;

    @InjectMocks
    private RequestController requestController;

    private final RequestSummaryResponse summary = RequestSummaryResponse.builder().build();

    @Test
    void listing_requests_should_return_200_with_the_list() {
        when(userService.validateUser(1)).thenReturn(UserResponse.builder().email("joe@example.com").build());
        when(requestService.getForUser("joe@example.com")).thenReturn(List.of(summary));

        ResponseEntity<List<RequestSummaryResponse>> response = requestController.getForUser(1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(summary);
    }

    @Test
    void listing_should_ask_only_for_the_validated_users_own_requests() {
        when(userService.validateUser(1)).thenReturn(UserResponse.builder().email("joe@example.com").build());
        when(requestService.getForUser("joe@example.com")).thenReturn(List.of());

        requestController.getForUser(1);

        verify(requestService).getForUser("joe@example.com");
    }
}
