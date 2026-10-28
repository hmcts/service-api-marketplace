package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 15, 9, 0);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClockService clockService;

    @InjectMocks
    private UserService userService;

    @Test
    void listing_users_should_come_back_in_row_order() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(saved(1L, OID)));
        when(userRepository.findAll(Sort.by("id"))).thenReturn(List.of(saved(1L, OID), saved(2L, OTHER_OID)));

        assertThat(userService.list(OID)).extracting(UserResponse::userReference)
            .containsExactly("user001", "user002");
    }

    @Test
    void listing_users_should_need_a_caller_this_service_knows() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.list(OID))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(userRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void numeric_user_id_should_be_read_as_the_row_id() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(saved(1L, OID)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(saved(2L, OTHER_OID)));

        assertThat(userService.get(OID, "2").userReference()).isEqualTo("user002");
    }

    @Test
    void anything_else_should_be_read_as_an_entra_object_id() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(saved(1L, OID)));
        when(userRepository.findByEntraObjectId(OTHER_OID)).thenReturn(Optional.of(saved(2L, OTHER_OID)));

        assertThat(userService.get(OID, "  " + OTHER_OID.toUpperCase(java.util.Locale.UK) + "  ").id())
            .isEqualTo(2L);
    }

    @Test
    void an_identifier_matching_nobody_should_be_not_found() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(saved(1L, OID)));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(OID, "99"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void missing_requesting_user_id_should_be_rejected_before_any_lookup() {
        assertThatThrownBy(() -> userService.requireSignedIn(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing requestingUserId");

        verifyNoInteractions(userRepository);
    }

    @Test
    void blank_requesting_user_id_should_be_rejected_before_any_lookup() {
        assertThatThrownBy(() -> userService.requireSignedIn("   "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing requestingUserId");
    }

    @Test
    void requesting_user_id_should_be_looked_up_trimmed_and_lowercased() {
        UserEntity found = new UserEntity();
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(found));

        assertThat(userService.requireSignedIn("  " + OID.toUpperCase(java.util.Locale.UK) + "  "))
            .isSameAs(found);
    }

    @Test
    void requesting_user_with_no_row_should_be_told_to_sign_in_again() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requireSignedIn(OID))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void the_name_prefix_should_pad_the_user_id_to_three_digits() {
        UserEntity user = new UserEntity();
        user.setId(7L);

        assertThat(UserService.namePrefix(user)).isEqualTo("amp-user007-");
    }

    @Test
    void first_sign_in_should_create_an_active_user() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 1L));

        UserResponse result = userService.signIn(
            new SessionRequest(OID.toUpperCase(), "Joe@Example.com", "Joe Bloggs"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.entraObjectId()).isEqualTo(OID);
        assertThat(result.email()).isEqualTo("joe@example.com");
        assertThat(result.name()).isEqualTo("Joe Bloggs");
        assertThat(result.userReference()).isEqualTo("user001");
        assertThat(result.status()).isEqualTo(UserEntity.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW_UTC);
        assertThat(result.lastLoginAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void signing_in_again_should_update_names_and_last_login_but_not_created_at() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com"))
            .thenReturn(Optional.of(existing(OID, "joe@example.com")));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.signIn(new SessionRequest(OID, "joe@example.com", "Joseph Bloggs"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getEmail()).isEqualTo("joe@example.com");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Joseph");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(CREATED);
        assertThat(saved.getValue().getLastLoginAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void signing_in_again_should_find_the_same_user_by_email() {
        UserEntity joe = existing(OID, "joe@example.com");
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.of(joe));
        when(userRepository.saveAndFlush(joe)).thenReturn(joe);

        assertThat(userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe Bloggs")).id())
            .isEqualTo(7L);
    }

    @Test
    void missing_name_should_be_saved_as_empty() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 1L));

        UserResponse result = userService.signIn(new SessionRequest(OID, "joe@example.com", null));

        assertThat(result.name()).isEmpty();
    }

    @Test
    void an_email_arriving_with_a_new_object_id_should_take_over_that_row() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com"))
            .thenReturn(Optional.of(existing(OTHER_OID, "joe@example.com")));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe"));

        // The row keeps its id, so the applications and subscription names that hang off it stay
        // reachable; only the object id Entra now issues for that person changes.
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getEntraObjectId()).isEqualTo(OID);
    }

    @Test
    void an_inactive_user_should_throw_403() {
        UserEntity suspended = existing(OID, "joe@example.com");
        suspended.setStatus("SUSPENDED");
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.of(suspended));

        assertStatus(() -> userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe")),
            HttpStatus.FORBIDDEN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void an_object_id_that_is_not_a_guid_should_throw_400_without_touching_the_database() {
        assertStatus(() -> userService.signIn(new SessionRequest("not-an-oid", "joe@example.com", "")),
            HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    @Test
    void losing_a_first_sign_in_race_should_retry_and_update_the_winning_row() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing(OID, "joe@example.com")));
        when(userRepository.saveAndFlush(any(UserEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"))
            .thenAnswer(call -> call.getArgument(0));

        UserResponse result = userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe"));

        assertThat(result.id()).isEqualTo(7L);
        verify(userRepository, times(2)).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void clashing_twice_should_throw_409() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertStatus(() -> userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe")),
            HttpStatus.CONFLICT);
        verify(userRepository, times(2)).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void the_name_should_be_split_at_the_first_space_into_first_and_last_name() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 1L));

        userService.signIn(new SessionRequest(OID, "joe@example.com", "  Ada  King Lovelace "));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(saved.getValue().getLastName()).isEqualTo("King Lovelace");
    }

    @Test
    void the_user_reference_should_be_formatted_from_the_id() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 7L));

        UserResponse result = userService.signIn(new SessionRequest(OID, "joe@example.com", "Joe"));

        assertThat(result.userReference()).isEqualTo("user007");
        assertThat(result.name()).isEqualTo("Joe");
    }

    private static UserEntity existing(final String oid, final String email) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEntraObjectId(oid);
        user.setEmail(email);
        user.setFirstName("Joe");
        user.setLastName("Bloggs");
        user.setStatus(UserEntity.ACTIVE);
        user.setCreatedAt(CREATED);
        user.setLastLoginAt(CREATED);
        return user;
    }

    private static UserEntity saved(final long id, final String oid) {
        return withId(existing(oid, "user" + id + "@hmcts.net"), id);
    }

    private static UserEntity withId(final UserEntity user, final long id) {
        user.setId(id);
        return user;
    }

    private static void assertStatus(final org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                     final HttpStatus status) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }
}
