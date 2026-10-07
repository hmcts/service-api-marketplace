package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AuthResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.MeResponse;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final OrganisationEntity HMCTS = OrganisationEntity.builder().id(3).name("HMCTS").build();

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private TokenService tokenService;

    @Mock
    private EntraUserClient entraUsers;

    @InjectMocks
    private AccountService accountService;

    private final RegisterRequest valid = RegisterRequest.builder()
        .firstName("Joe").lastName("Bloggs").email("joe.bloggs@example.com")
        .organisation("HMCTS").role("consumer").password(PASSWORD).build();

    private final UserEntity stored = UserEntity.builder()
        .id(11).organisation(HMCTS).firstName("Joe").lastName("Bloggs").email("joe.bloggs@example.com")
        .passwordHash("stored-hash").status("ACTIVE").role("consumer").build();

    private void assertRejected(final RegisterRequest request, final HttpStatus status, final String message) {
        assertThatThrownBy(() -> accountService.register(request))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(status);
                assertThat(e.getReason()).isEqualTo(message);
            });
    }

    private void assertBadRequest(final RegisterRequest request, final String message) {
        assertRejected(request, HttpStatus.BAD_REQUEST, message);
        verify(userRepository, never()).save(any());
    }

    private void registrationWillSucceed() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");
    }

    // ----------------------------------------------------------------------------------- register

    @Test
    void registering_should_create_the_account_and_sign_the_caller_in() {
        registrationWillSucceed();

        AuthResponse response = accountService.register(valid);

        assertThat(response.token()).isEqualTo("the-token");
        assertThat(response.user().id()).isEqualTo(11);
        assertThat(response.user().firstName()).isEqualTo("Joe");
        assertThat(response.user().email()).isEqualTo("joe.bloggs@example.com");
        assertThat(response.user().role()).isEqualTo("consumer");
    }

    @Test
    void the_saved_account_should_hold_a_hash_a_lower_case_email_and_trimmed_names_never_the_password() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");

        accountService.register(valid.toBuilder()
            .firstName("  Joe ").lastName(" Bloggs  ").email("Joe.Bloggs@Example.COM").role("producer").build());

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Joe");
        assertThat(saved.getValue().getLastName()).isEqualTo("Bloggs");
        assertThat(saved.getValue().getEmail()).isEqualTo("joe.bloggs@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("stored-hash").isNotEqualTo(PASSWORD);
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getRole()).isEqualTo("producer");
        assertThat(saved.getValue().getOrganisation()).isEqualTo(HMCTS);
    }

    @Test
    void every_required_field_should_be_required() {
        assertBadRequest(valid.toBuilder().firstName(null).build(), "Missing required fields.");
        assertBadRequest(valid.toBuilder().lastName(" ").build(), "Missing required fields.");
        assertBadRequest(valid.toBuilder().email("").build(), "Missing required fields.");
        assertBadRequest(valid.toBuilder().role(null).build(), "Missing required fields.");
        assertBadRequest(valid.toBuilder().password(null).build(), "Missing required fields.");
        assertBadRequest(new RegisterRequest(), "Missing required fields.");
    }

    @Test
    void the_organisation_should_be_optional() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("Not specified")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");

        assertThat(accountService.register(valid.toBuilder().organisation(null).build()).token())
            .isEqualTo("the-token");
        assertThat(accountService.register(valid.toBuilder().organisation("   ").build()).token())
            .isEqualTo("the-token");
    }

    @Test
    void the_email_should_have_to_look_like_one() {
        assertBadRequest(valid.toBuilder().email("not-an-email").build(), "Enter a valid email address.");
        assertBadRequest(valid.toBuilder().email("two@@example.com").build(), "Enter a valid email address.");
        assertBadRequest(valid.toBuilder().email("no-dot@example").build(), "Enter a valid email address.");
        assertBadRequest(valid.toBuilder().email("has space@example.com").build(), "Enter a valid email address.");
        assertBadRequest(valid.toBuilder().email("a".repeat(250) + "@example.com").build(),
            "Enter a valid email address.");
    }

    @Test
    void the_role_should_be_consumer_or_producer() {
        assertBadRequest(valid.toBuilder().role("admin").build(), "Role must be \"consumer\" or \"producer\".");
        assertBadRequest(valid.toBuilder().role("Consumer").build(), "Role must be \"consumer\" or \"producer\".");
    }

    @Test
    void the_password_should_be_at_least_twelve_characters() {
        assertBadRequest(valid.toBuilder().password("elevenchars").build(),
            "Password must be at least 12 characters long.");
        verifyNoInteractions(passwordService);
    }

    @Test
    void password_of_exactly_twelve_characters_should_be_accepted() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash("twelvechars!")).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");

        assertThat(accountService.register(valid.toBuilder().password("twelvechars!").build()).token())
            .isEqualTo("the-token");
    }

    @Test
    void password_bcrypt_cannot_hash_should_be_refused_with_a_message_not_an_error() {
        assertBadRequest(valid.toBuilder().password("x".repeat(73)).build(),
            "Password must be no more than 72 bytes long.");
    }

    @Test
    void names_and_the_organisation_should_have_a_length_limit() {
        String message = "Names must be no more than 200 characters long.";
        assertBadRequest(valid.toBuilder().firstName("x".repeat(201)).build(), message);
        assertBadRequest(valid.toBuilder().lastName("x".repeat(201)).build(), message);
        assertBadRequest(valid.toBuilder().organisation("x".repeat(201)).build(), message);
    }

    @Test
    void registering_without_a_signing_secret_should_be_refused_before_anything_is_written() {
        ResponseStatusException unavailable = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no");
        doThrow(unavailable).when(tokenService).requireConfigured();

        assertThatThrownBy(() -> accountService.register(valid)).isSameAs(unavailable);

        verifyNoInteractions(userRepository, organisationRepository, passwordService);
    }

    @Test
    void an_email_that_is_already_registered_should_be_refused_without_saying_so() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.of(stored));

        assertRejected(valid, HttpStatus.CONFLICT, "An account with these details could not be created.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordService);
    }

    @Test
    void two_registrations_racing_for_one_email_should_give_the_loser_the_same_refusal() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertRejected(valid, HttpStatus.CONFLICT, "An account with these details could not be created.");
        verify(tokenService, never()).issue(any());
    }

    // ------------------------------------------------------------------------------ organisation

    @Test
    void an_organisation_that_exists_should_be_reused_whatever_its_case() {
        registrationWillSucceed();

        accountService.register(valid);

        verify(organisationRepository, never()).save(any());
    }

    @Test
    void an_organisation_that_does_not_exist_yet_should_be_created() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("Ministry of Testing")).thenReturn(Optional.empty());
        when(organisationRepository.save(any(OrganisationEntity.class))).thenReturn(HMCTS);
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");

        accountService.register(valid.toBuilder().organisation("  Ministry of Testing ").build());

        ArgumentCaptor<OrganisationEntity> created = ArgumentCaptor.forClass(OrganisationEntity.class);
        verify(organisationRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Ministry of Testing");
    }

    @Test
    void two_registrations_creating_the_same_organisation_at_once_should_share_it() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(HMCTS));
        when(organisationRepository.save(any(OrganisationEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate organisation"));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(stored);
        when(tokenService.issue(stored)).thenReturn("the-token");

        assertThat(accountService.register(valid).token()).isEqualTo("the-token");
    }

    @Test
    void an_organisation_that_cannot_be_created_or_found_should_surface_the_original_error() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("something else");
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.empty());
        when(organisationRepository.save(any(OrganisationEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> accountService.register(valid)).isSameAs(failure);
    }

    // --------------------------------------------------------------------------------------- login

    @Test
    void the_right_email_and_password_should_sign_in() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.of(stored));
        when(passwordService.matches(PASSWORD, "stored-hash")).thenReturn(true);
        when(tokenService.issue(stored)).thenReturn("the-token");

        AuthResponse response = accountService.login(
            LoginRequest.builder().email("joe.bloggs@example.com").password(PASSWORD).build());

        assertThat(response.token()).isEqualTo("the-token");
        assertThat(response.user().email()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void the_email_should_match_whatever_its_case_or_padding() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.of(stored));
        when(passwordService.matches(PASSWORD, "stored-hash")).thenReturn(true);
        when(tokenService.issue(stored)).thenReturn("the-token");

        assertThat(accountService.login(
            LoginRequest.builder().email(" Joe.Bloggs@EXAMPLE.com ").password(PASSWORD).build()).token())
            .isEqualTo("the-token");
    }

    @Test
    void both_fields_should_be_required_to_sign_in() {
        for (LoginRequest request : List.of(
            LoginRequest.builder().email(null).password(PASSWORD).build(),
            LoginRequest.builder().email("joe.bloggs@example.com").password(" ").build(),
            new LoginRequest())) {
            assertThatThrownBy(() -> accountService.login(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("Email and password are required.");
                });
        }
        verifyNoInteractions(userRepository);
    }

    @Test
    void wrong_password_unknown_email_and_inactive_account_should_all_get_the_same_answer() {
        UserEntity inactive = stored.toBuilder().status("DISABLED").build();
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.of(stored));
        when(passwordService.matches("wrong-password-1", "stored-hash")).thenReturn(false);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));
        when(passwordService.matches(PASSWORD, "stored-hash")).thenReturn(true);

        for (LoginRequest request : List.of(
            LoginRequest.builder().email("joe.bloggs@example.com").password("wrong-password-1").build(),
            LoginRequest.builder().email("nobody@example.com").password(PASSWORD).build(),
            LoginRequest.builder().email("inactive@example.com").password(PASSWORD).build())) {
            assertThatThrownBy(() -> accountService.login(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getReason()).isEqualTo("Incorrect email or password.");
                });
        }
        verify(tokenService, never()).issue(any());
    }

    @Test
    void an_unknown_email_should_still_cost_a_password_check_so_timing_does_not_give_it_away() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.login(
            LoginRequest.builder().email("nobody@example.com").password(PASSWORD).build()))
            .isInstanceOf(ResponseStatusException.class);

        verify(passwordService).spendTimeAsIfChecking(PASSWORD);
        verify(passwordService, never()).matches(anyString(), anyString());
    }

    @Test
    void signing_in_without_a_signing_secret_should_be_refused_before_the_account_is_looked_up() {
        ResponseStatusException unavailable = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no");
        doThrow(unavailable).when(tokenService).requireConfigured();

        assertThatThrownBy(() -> accountService.login(
            LoginRequest.builder().email("joe.bloggs@example.com").password(PASSWORD).build()))
            .isSameAs(unavailable);

        verifyNoInteractions(userRepository);
    }

    // ----------------------------------------------------------------------------------------- me

    private void assertNotSignedIn(final String header, final String message) {
        assertThatThrownBy(() -> accountService.currentUser(header))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getReason()).isEqualTo(message);
            });
    }

    @Test
    void valid_token_should_identify_the_user() {
        when(tokenService.verify("good-token")).thenReturn(
            Optional.of(new TokenService.Claims(11, "joe.bloggs@example.com", "consumer")));
        when(userRepository.findById(11)).thenReturn(Optional.of(stored));

        MeResponse response = accountService.currentUser("Bearer good-token");

        assertThat(response.user().id()).isEqualTo(11);
        assertThat(response.user().email()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void no_credentials_should_mean_not_signed_in() {
        assertNotSignedIn(null, "Not signed in.");
        assertNotSignedIn("Basic am9lOnB3", "Not signed in.");
        assertNotSignedIn("Bearer ", "Not signed in.");
        assertNotSignedIn("bearer lower-case-scheme", "Not signed in.");
        verifyNoInteractions(tokenService);
    }

    @Test
    void token_that_does_not_verify_should_mean_the_session_has_expired() {
        when(tokenService.verify("stale-token")).thenReturn(Optional.empty());

        assertNotSignedIn("Bearer stale-token", "Session expired. Please sign in again.");
    }

    @Test
    void valid_token_for_an_account_that_no_longer_exists_should_mean_not_signed_in() {
        when(tokenService.verify("good-token")).thenReturn(
            Optional.of(new TokenService.Claims(99, "gone@example.com", "consumer")));
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertNotSignedIn("Bearer good-token", "Not signed in.");
    }

    @Test
    void valid_token_for_a_deactivated_account_should_stop_working_at_once() {
        when(tokenService.verify("good-token")).thenReturn(
            Optional.of(new TokenService.Claims(11, "joe.bloggs@example.com", "consumer")));
        when(userRepository.findById(11)).thenReturn(Optional.of(stored.toBuilder().status("DISABLED").build()));

        assertNotSignedIn("Bearer good-token", "Not signed in.");
    }

    // --------------------------------------------------------------- the person's user in Entra

    private void entraWillCreate() {
        when(entraUsers.enabled()).thenReturn(true);
        when(entraUsers.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD)).thenReturn("entra-oid-1");
    }

    private void everythingBeforeEntraWillSucceed() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenReturn("stored-hash");
    }

    @Test
    void registering_with_entra_should_create_the_user_after_hashing_and_before_saving() {
        registrationWillSucceed();
        entraWillCreate();

        accountService.register(valid);

        InOrder order = inOrder(passwordService, entraUsers, userRepository);
        order.verify(passwordService).hash(PASSWORD);
        order.verify(entraUsers).createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD);
        order.verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void the_entra_object_id_should_be_kept_on_the_account() {
        registrationWillSucceed();
        entraWillCreate();

        accountService.register(valid);

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEntraObjectId()).isEqualTo("entra-oid-1");
    }

    @Test
    void registering_without_entra_should_never_touch_it_and_keep_no_object_id() {
        registrationWillSucceed();

        accountService.register(valid);

        verify(entraUsers, never()).createUser(any(), any(), any(), any());
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEntraObjectId()).isNull();
    }

    @Test
    void an_email_entra_already_has_should_be_refused_exactly_like_one_registered_here() {
        everythingBeforeEntraWillSucceed();
        when(entraUsers.enabled()).thenReturn(true);
        when(entraUsers.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD))
            .thenThrow(new EntraUserClient.AlreadyExists());

        assertRejected(valid, HttpStatus.CONFLICT, "An account with these details could not be created.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void password_entra_refuses_should_say_so_and_save_nothing() {
        everythingBeforeEntraWillSucceed();
        when(entraUsers.enabled()).thenReturn(true);
        when(entraUsers.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD))
            .thenThrow(new EntraUserClient.PasswordRejected());

        assertRejected(valid, HttpStatus.BAD_REQUEST, AccountService.PASSWORD_REJECTED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void failure_at_entra_should_save_nothing_and_surface() {
        everythingBeforeEntraWillSucceed();
        when(entraUsers.enabled()).thenReturn(true);
        when(entraUsers.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create the account."));

        assertRejected(valid, HttpStatus.BAD_GATEWAY, "Could not create the account.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void the_hashing_failing_should_not_leave_an_entra_user_behind() {
        when(userRepository.findByEmail("joe.bloggs@example.com")).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(HMCTS));
        when(passwordService.hash(PASSWORD)).thenThrow(new IllegalStateException("hashing failed"));

        assertThatThrownBy(() -> accountService.register(valid)).isInstanceOf(IllegalStateException.class);

        verify(entraUsers, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void losing_the_email_race_should_delete_the_entra_user_again() {
        everythingBeforeEntraWillSucceed();
        entraWillCreate();
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertRejected(valid, HttpStatus.CONFLICT, "An account with these details could not be created.");

        verify(entraUsers).undoCreate("entra-oid-1");
    }

    @Test
    void any_other_failure_saving_should_delete_the_entra_user_again_and_surface() {
        everythingBeforeEntraWillSucceed();
        entraWillCreate();
        when(userRepository.save(any(UserEntity.class))).thenThrow(new IllegalStateException("db is down"));

        assertThatThrownBy(() -> accountService.register(valid)).isInstanceOf(IllegalStateException.class);

        verify(entraUsers).undoCreate("entra-oid-1");
    }

    @Test
    void failure_saving_without_entra_should_have_nothing_to_undo() {
        everythingBeforeEntraWillSucceed();
        when(userRepository.save(any(UserEntity.class))).thenThrow(new IllegalStateException("db is down"));

        assertThatThrownBy(() -> accountService.register(valid)).isInstanceOf(IllegalStateException.class);

        verify(entraUsers, never()).undoCreate(any());
    }
}
