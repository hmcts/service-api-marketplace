package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.PublishResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.PublishService;
import uk.gov.hmcts.cp.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishControllerTest {

    @Mock
    private PublishService publishService;

    @Mock
    private UserService userService;

    @InjectMocks
    private PublishController publishController;

    private final PublishRequest publishRequest = PublishRequest.builder().build();
    private final UserResponse userResponse = UserResponse.builder().id(1).build();
    private final PublishResponse publishResponse = PublishResponse.builder().build();

    @Test
    void submitting_a_valid_request_should_return_201_with_the_publish_response() {
        when(userService.validateUser(1)).thenReturn(userResponse);
        when(publishService.submit(userResponse.getId(), publishRequest)).thenReturn(publishResponse);

        ResponseEntity<PublishResponse> response = publishController.submit(1, publishRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(publishResponse);
    }

    @Test
    void submitting_should_attribute_the_request_to_the_validated_user_not_the_header() {
        // The header names a user; the service is given the id the lookup confirmed, so a
        // request cannot be stored against an id the backend never verified.
        UserResponse validated = UserResponse.builder().id(7).build();
        when(userService.validateUser(1)).thenReturn(validated);
        when(publishService.submit(7, publishRequest)).thenReturn(publishResponse);

        publishController.submit(1, publishRequest);

        verify(publishService).submit(7, publishRequest);
    }

    @Test
    void getting_all_publish_requests_should_return_200_with_the_list() {
        when(publishService.getAll()).thenReturn(List.of(publishResponse));

        ResponseEntity<List<PublishResponse>> response = publishController.getAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(publishResponse);
    }

    @Test
    void deleting_a_publish_request_should_return_204() {
        String reference = "PR-2026-414D8U";

        ResponseEntity<Void> response = publishController.delete(reference);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(publishService).delete(reference);
    }
}
