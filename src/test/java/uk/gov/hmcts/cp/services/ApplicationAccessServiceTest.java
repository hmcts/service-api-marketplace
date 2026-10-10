package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AccountResponse;
import uk.gov.hmcts.cp.domain.MeResponse;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.ApplicationTeamMemberRepository;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Access;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Caller;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationAccessServiceTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f8a1c94-6b2e-4d51-9a77-0e1c5b8d4f23");

    @Mock
    private AccountService accountService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationTeamMemberRepository teamMemberRepository;

    @InjectMocks
    private ApplicationAccessService access;

    private final ApplicationEntity application = ApplicationEntity.builder()
        .id(10L).publicId(PUBLIC_ID).user(UserEntity.builder().id(1).email("olive@example.com").build()).build();

    private void signedInAs(final int id, final String email) {
        when(accountService.currentUser("Bearer t"))
            .thenReturn(new MeResponse(new AccountResponse(id, "F", "L", email, "consumer")));
    }

    private void isTeamMember(final String email, final String role) {
        when(teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, email))
            .thenReturn(Optional.of(ApplicationTeamMemberEntity.builder().email(email).role(role).build()));
    }

    private void assertStatus(final Runnable action, final HttpStatus status, final String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(message);
        });
    }

    @Test
    void the_caller_should_be_the_id_and_email_from_the_signed_in_account() {
        signedInAs(5, "dan@example.com");

        assertThat(access.caller("Bearer t")).isEqualTo(new Caller(5, "dan@example.com"));
    }

    @Test
    void not_being_signed_in_should_be_refused_before_any_application_is_looked_up() {
        ResponseStatusException unauthorized = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        when(accountService.currentUser(null)).thenThrow(unauthorized);

        assertThatThrownBy(() -> access.require(null, PUBLIC_ID.toString(), ViewerRole.DEVELOPER))
            .isSameAs(unauthorized);

        verifyNoInteractions(applicationRepository);
    }

    @Test
    void the_owner_should_have_owner_access() {
        signedInAs(1, "olive@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));

        Access granted = access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.OWNER);

        assertThat(granted.role()).isEqualTo(ViewerRole.OWNER);
        assertThat(granted.application()).isSameAs(application);
        assertThat(granted.caller()).isEqualTo(new Caller(1, "olive@example.com"));
        verify(teamMemberRepository, never()).findByApplicationAndEmailIgnoreCase(any(), any());
    }

    @Test
    void team_member_should_have_the_role_they_were_invited_with() {
        signedInAs(2, "dan@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        isTeamMember("dan@example.com", "developer");

        assertThat(access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.DEVELOPER).role())
            .isEqualTo(ViewerRole.DEVELOPER);
    }

    @Test
    void an_administrator_should_pass_a_check_for_administrator() {
        signedInAs(3, "ada@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        isTeamMember("ada@example.com", "administrator");

        assertThat(access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.ADMINISTRATOR).role())
            .isEqualTo(ViewerRole.ADMINISTRATOR);
    }

    @Test
    void too_low_a_role_should_be_forbidden_not_hidden() {
        signedInAs(2, "dan@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        isTeamMember("dan@example.com", "developer");

        assertStatus(() -> access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.ADMINISTRATOR),
            HttpStatus.FORBIDDEN, "You do not have permission to do this.");
    }

    @Test
    void an_administrator_should_not_be_able_to_do_what_only_the_owner_can() {
        signedInAs(3, "ada@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        isTeamMember("ada@example.com", "administrator");

        assertStatus(() -> access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.OWNER),
            HttpStatus.FORBIDDEN, "You do not have permission to do this.");
    }

    @Test
    void someone_with_no_access_should_get_exactly_the_answer_a_missing_application_gets() {
        signedInAs(9, "stan@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        when(teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, "stan@example.com"))
            .thenReturn(Optional.empty());

        assertStatus(() -> access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.DEVELOPER),
            HttpStatus.NOT_FOUND, "Application not found.");
    }

    @Test
    void an_application_that_does_not_exist_should_be_not_found() {
        signedInAs(1, "olive@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.empty());

        assertStatus(() -> access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.DEVELOPER),
            HttpStatus.NOT_FOUND, "Application not found.");
    }

    @Test
    void an_id_that_is_not_a_uuid_should_be_not_found_without_touching_the_database() {
        signedInAs(1, "olive@example.com");

        assertStatus(() -> access.require("Bearer t", "not-a-uuid", ViewerRole.DEVELOPER),
            HttpStatus.NOT_FOUND, "Application not found.");

        verifyNoInteractions(applicationRepository);
    }

    @Test
    void stored_team_role_that_is_not_a_known_one_should_grant_nothing() {
        signedInAs(2, "dan@example.com");
        when(applicationRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(application));
        isTeamMember("dan@example.com", "owner");

        assertStatus(() -> access.require("Bearer t", PUBLIC_ID.toString(), ViewerRole.DEVELOPER),
            HttpStatus.NOT_FOUND, "Application not found.");
    }

    @Test
    void parsing_should_accept_a_uuid_and_reject_anything_else() {
        assertThat(ApplicationAccessService.parse(PUBLIC_ID.toString())).contains(PUBLIC_ID);
        assertThat(ApplicationAccessService.parse("nope")).isEmpty();
        assertThat(ApplicationAccessService.parse("")).isEmpty();
    }
}
