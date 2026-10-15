package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.EntraUserRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private final EntraUserRequest request = new EntraUserRequest("joe@example.com", "Joe", "Bloggs");

    @Test
    void upserting_an_entra_user_should_return_the_user_from_the_service() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 9, 0);
        UserResponse response = new UserResponse(1L, OID, "joe@example.com", "Joe", "Bloggs", "ACTIVE", now, now);
        when(userService.upsertEntraUser(OID, request)).thenReturn(response);

        assertThat(userController.upsertEntraUser(OID, request)).isEqualTo(response);
    }

    @Test
    void upserting_an_entra_user_should_pass_on_a_refusal_from_the_service() {
        when(userService.upsertEntraUser(OID, request))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "This email address is already used."));

        assertThatThrownBy(() -> userController.upsertEntraUser(OID, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }
}
