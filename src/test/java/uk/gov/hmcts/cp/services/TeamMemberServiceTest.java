package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AddTeamMemberRequest;
import uk.gov.hmcts.cp.domain.TeamMemberEnvelope;
import uk.gov.hmcts.cp.domain.TeamMembersResponse;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.ApplicationTeamMemberRepository;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Access;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Caller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String APP = "3f8a1c94-6b2e-4d51-9a77-0e1c5b8d4f23";

    @Mock
    private ApplicationAccessService access;

    @Mock
    private ApplicationTeamMemberRepository teamMemberRepository;

    @Spy
    private ApplicationViewFactory views = new ApplicationViewFactory();

    @Mock
    private ClockService clockService;

    @InjectMocks
    private TeamMemberService service;

    private final UserEntity owner = UserEntity.builder().id(1).email("Olive@Example.com").build();
    private final ApplicationEntity application = ApplicationEntity.builder().id(10L).user(owner).build();

    @BeforeEach
    void signedInAsAnAdministrator() {
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-02T10:00:00Z"));
        lenient().when(access.require("Bearer t", APP, ViewerRole.ADMINISTRATOR))
            .thenReturn(new Access(new Caller(3, "ada@example.com"), application, ViewerRole.ADMINISTRATOR));
    }

    private AddTeamMemberRequest add(final String email, final String role) {
        return AddTeamMemberRequest.builder().email(email).role(role).build();
    }

    private void assertRejected(final AddTeamMemberRequest request, final HttpStatus status, final String message) {
        assertThatThrownBy(() -> service.add("Bearer t", APP, request))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(status);
                assertThat(e.getReason()).isEqualTo(message);
            });
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void listing_should_show_the_owners_email_and_every_member_oldest_first() {
        ApplicationTeamMemberEntity dan = ApplicationTeamMemberEntity.builder().publicId(MEMBER_ID)
            .email("dan@example.com").role("developer").addedAt(LocalDateTime.parse("2026-10-02T09:00:00")).build();
        when(access.require("Bearer t", APP, ViewerRole.DEVELOPER))
            .thenReturn(new Access(new Caller(2, "dan@example.com"), application, ViewerRole.DEVELOPER));
        when(teamMemberRepository.findByApplicationOrderByAddedAtAsc(application)).thenReturn(List.of(dan));

        TeamMembersResponse response = service.list("Bearer t", APP);

        assertThat(response.ownerEmail()).isEqualTo("Olive@Example.com");
        assertThat(response.teamMembers()).hasSize(1);
        assertThat(response.teamMembers().get(0).email()).isEqualTo("dan@example.com");
        assertThat(response.teamMembers().get(0).id()).isEqualTo(MEMBER_ID.toString());
    }

    @Test
    void adding_should_save_the_member_with_a_lower_case_email_and_the_chosen_role() {
        when(teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, "dan@example.com"))
            .thenReturn(Optional.empty());
        when(teamMemberRepository.save(any(ApplicationTeamMemberEntity.class)))
            .thenAnswer(invocation -> ((ApplicationTeamMemberEntity) invocation.getArgument(0)).toBuilder()
                .publicId(MEMBER_ID).build());

        TeamMemberEnvelope response = service.add("Bearer t", APP, add("  Dan@Example.COM ", "developer"));

        ArgumentCaptor<ApplicationTeamMemberEntity> saved = ArgumentCaptor.forClass(ApplicationTeamMemberEntity.class);
        verify(teamMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("dan@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo("developer");
        assertThat(saved.getValue().getApplication()).isSameAs(application);
        assertThat(saved.getValue().getAddedAt()).isEqualTo(LocalDateTime.parse("2026-10-02T10:00:00"));
        assertThat(response.teamMember().email()).isEqualTo("dan@example.com");
        assertThat(response.teamMember().role()).isEqualTo("developer");
    }

    @Test
    void an_email_is_required_and_has_to_look_like_one() {
        assertRejected(add(null, "developer"), HttpStatus.BAD_REQUEST, "Enter an email address.");
        assertRejected(add("   ", "developer"), HttpStatus.BAD_REQUEST, "Enter an email address.");
        assertRejected(add("nope", "developer"), HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        assertRejected(add("a".repeat(250) + "@example.com", "developer"), HttpStatus.BAD_REQUEST,
            "Enter a valid email address.");
    }

    @Test
    void the_role_has_to_be_developer_or_administrator() {
        assertRejected(add("dan@example.com", null), HttpStatus.BAD_REQUEST, "Select a permission level.");
        assertRejected(add("dan@example.com", "owner"), HttpStatus.BAD_REQUEST, "Select a permission level.");
        assertRejected(add("dan@example.com", "king"), HttpStatus.BAD_REQUEST, "Select a permission level.");
    }

    @Test
    void you_cannot_add_yourself() {
        assertRejected(add("ADA@example.com", "developer"), HttpStatus.BAD_REQUEST,
            "You already have access to this application.");
    }

    @Test
    void you_cannot_add_the_owner_as_a_team_member() {
        assertRejected(add("olive@example.com", "developer"), HttpStatus.BAD_REQUEST,
            "That person already owns this application.");
    }

    @Test
    void the_same_person_cannot_be_added_twice() {
        when(teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, "dan@example.com"))
            .thenReturn(Optional.of(ApplicationTeamMemberEntity.builder().email("dan@example.com").build()));

        assertRejected(add("dan@example.com", "developer"), HttpStatus.CONFLICT,
            "That person is already a team member on this application.");
    }

    @Test
    void two_adds_racing_for_one_person_should_give_the_loser_the_same_refusal() {
        when(teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, "dan@example.com"))
            .thenReturn(Optional.empty());
        when(teamMemberRepository.save(any(ApplicationTeamMemberEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.add("Bearer t", APP, add("dan@example.com", "developer")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getReason()).isEqualTo("That person is already a team member on this application.");
            });
    }

    @Test
    void removing_a_member_should_delete_them() {
        ApplicationTeamMemberEntity dan = ApplicationTeamMemberEntity.builder().publicId(MEMBER_ID)
            .email("dan@example.com").build();
        when(teamMemberRepository.findByPublicIdAndApplication(MEMBER_ID, application)).thenReturn(Optional.of(dan));

        assertThat(service.remove("Bearer t", APP, MEMBER_ID.toString()).ok()).isTrue();

        verify(teamMemberRepository).delete(dan);
    }

    @Test
    void you_cannot_remove_yourself() {
        ApplicationTeamMemberEntity ada = ApplicationTeamMemberEntity.builder().publicId(MEMBER_ID)
            .email("Ada@Example.com").build();
        when(teamMemberRepository.findByPublicIdAndApplication(MEMBER_ID, application)).thenReturn(Optional.of(ada));

        assertThatThrownBy(() -> service.remove("Bearer t", APP, MEMBER_ID.toString()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("You cannot remove yourself from this application.");
            });
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removing_someone_who_is_not_on_the_team_should_be_not_found() {
        when(teamMemberRepository.findByPublicIdAndApplication(MEMBER_ID, application)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove("Bearer t", APP, MEMBER_ID.toString()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getReason()).isEqualTo("Team member not found.");
            });
    }

    @Test
    void member_id_that_is_not_a_uuid_should_be_not_found_not_a_server_error() {
        assertThatThrownBy(() -> service.remove("Bearer t", APP, "not-a-uuid"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(teamMemberRepository, never()).delete(any());
    }
}
