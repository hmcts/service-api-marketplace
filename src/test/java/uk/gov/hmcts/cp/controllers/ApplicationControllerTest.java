package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.ApplicationRequest;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.ApplicationService;
import uk.gov.hmcts.cp.services.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ApplicationController applicationController;

    private final ApplicationRequest applicationRequest = ApplicationRequest.builder().build();
    private final UserResponse userResponse = UserResponse.builder().id(1).build();

    @Test
    void registering_a_valid_application_should_return_201_with_the_application_response() {
        ApplicationResponse serviceResponse = ApplicationResponse.builder().build();
        when(userService.validateUser(1)).thenReturn(userResponse);
        when(applicationService.register(userResponse.getId(), applicationRequest)).thenReturn(serviceResponse);

        ResponseEntity<ApplicationResponse> response = applicationController.register(1, applicationRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
    }

    @Test
    void listing_applications_should_return_200_with_the_users_applications() {
        List<ApplicationResponse> serviceResponse = List.of(ApplicationResponse.builder().build());
        when(userService.validateUser(1)).thenReturn(userResponse);
        when(applicationService.getForUser(userResponse.getId())).thenReturn(serviceResponse);

        ResponseEntity<List<ApplicationResponse>> response = applicationController.getForUser(1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
    }
}
