package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

    @Mock
    private UserService users;

    @InjectMocks
    private UserController controller;

    private final UserResponse ada = new UserResponse(1L, "user001", OID, "ada.lovelace@hmcts.net",
        "Ada Lovelace", "ACTIVE", LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 10, 16, 9, 0));

    @Test
    void listing_should_return_what_the_service_found() {
        when(users.list(OID)).thenReturn(List.of(ada));

        assertThat(controller.listUsers(OID)).containsExactly(ada);
    }

    @Test
    void reading_one_should_pass_the_identifier_through_untouched() {
        when(users.get(OID, "1")).thenReturn(ada);

        assertThat(controller.getUser(OID, "1")).isEqualTo(ada);
    }
}
