package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private LoginController loginController;

    @Test
    void logging_in_with_a_known_email_should_return_200_with_the_user() {
        UserResponse user = UserResponse.builder().build();
        when(userService.getUser("joe.bloggs@example.com")).thenReturn(user);

        ResponseEntity<UserResponse> result = loginController.login(LoginRequest.builder()
            .email("joe.bloggs@example.com")
            .password("anything")
            .build());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(user);
    }

    @Test
    void logging_in_should_ignore_the_password_entirely() {
        UserResponse user = UserResponse.builder().build();
        when(userService.getUser("joe.bloggs@example.com")).thenReturn(user);

        ResponseEntity<UserResponse> first = loginController.login(LoginRequest.builder()
            .email("joe.bloggs@example.com").password("correct-horse").build());
        ResponseEntity<UserResponse> second = loginController.login(LoginRequest.builder()
            .email("joe.bloggs@example.com").password("obviously-wrong").build());

        // Documents the stub: any password succeeds. Delete this when real verification lands.
        assertThat(first.getBody()).isEqualTo(second.getBody());
    }

    @Test
    void the_password_should_not_appear_in_the_request_tostring() {
        LoginRequest request = LoginRequest.builder()
            .email("joe.bloggs@example.com")
            .password("s3cr3t")
            .build();

        assertThat(request.toString()).doesNotContain("s3cr3t");
    }
}
