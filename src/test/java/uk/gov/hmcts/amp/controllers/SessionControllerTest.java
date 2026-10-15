package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

    @Mock
    private UserService userService;

    @InjectMocks
    private SessionController sessionController;

    private final SessionRequest request = new SessionRequest(OID, "joe@example.com", "Joe Bloggs");

    @Test
    void starting_a_session_should_return_the_user_from_the_service() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 9, 0);
        UserResponse response = new UserResponse(1L, "user001", OID, "joe@example.com", "Joe Bloggs", "ACTIVE",
            now, now);
        when(userService.signIn(request)).thenReturn(response);

        assertThat(sessionController.startSession(request)).isEqualTo(response);
    }

    @Test
    void starting_a_session_should_pass_on_a_refusal_from_the_service() {
        when(userService.signIn(request))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "This email address is already used."));

        assertThatThrownBy(() -> sessionController.startSession(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }
}
