package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.EntraUserRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
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
    void first_sign_in_should_create_an_active_user() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 1L));

        UserResponse result = userService.upsertEntraUser(OID.toUpperCase(),
            new EntraUserRequest("Joe@Example.com", " Joe ", "Bloggs"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.entraObjectId()).isEqualTo(OID);
        assertThat(result.email()).isEqualTo("joe@example.com");
        assertThat(result.firstName()).isEqualTo("Joe");
        assertThat(result.lastName()).isEqualTo("Bloggs");
        assertThat(result.status()).isEqualTo(UserEntity.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW_UTC);
        assertThat(result.lastLoginAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void signing_in_again_should_update_email_names_and_last_login_but_not_created_at() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(existing(OID, "joe@example.com")));
        when(userRepository.findByEmail("joseph@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        userService.upsertEntraUser(OID, new EntraUserRequest("joseph@example.com", "Joseph", "Bloggs"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getEmail()).isEqualTo("joseph@example.com");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Joseph");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(CREATED);
        assertThat(saved.getValue().getLastLoginAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void signing_in_with_an_unchanged_email_should_find_the_user_by_it_and_still_succeed() {
        UserEntity joe = existing(OID, "joe@example.com");
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(joe));
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.of(joe));
        when(userRepository.saveAndFlush(joe)).thenReturn(joe);

        assertThat(userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", "Joe", "Bloggs")).id())
            .isEqualTo(7L);
    }

    @Test
    void missing_names_should_be_saved_as_empty() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> withId(call.getArgument(0), 1L));

        UserResponse result = userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", null, null));

        assertThat(result.firstName()).isEmpty();
        assertThat(result.lastName()).isEmpty();
    }

    @Test
    void an_email_held_by_another_entra_user_should_throw_409() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("joe@example.com"))
            .thenReturn(Optional.of(existing(OTHER_OID, "joe@example.com")));

        assertStatus(() -> userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", "Joe", "")),
            HttpStatus.CONFLICT);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void an_inactive_user_should_throw_403() {
        UserEntity suspended = existing(OID, "joe@example.com");
        suspended.setStatus("SUSPENDED");
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(suspended));

        assertStatus(() -> userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", "Joe", "")),
            HttpStatus.FORBIDDEN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void an_object_id_that_is_not_a_guid_should_throw_400_without_touching_the_database() {
        assertStatus(() -> userService.upsertEntraUser("not-an-oid", new EntraUserRequest("joe@example.com", "", "")),
            HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    @Test
    void losing_a_first_sign_in_race_should_retry_and_update_the_winning_row() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing(OID, "joe@example.com")));
        when(userRepository.findByEmail("joe@example.com"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing(OID, "joe@example.com")));
        when(userRepository.saveAndFlush(any(UserEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"))
            .thenAnswer(call -> call.getArgument(0));

        UserResponse result = userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", "Joe", ""));

        assertThat(result.id()).isEqualTo(7L);
        verify(userRepository, times(2)).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void clashing_twice_should_throw_409() {
        when(clockService.now()).thenReturn(NOW);
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("joe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertStatus(() -> userService.upsertEntraUser(OID, new EntraUserRequest("joe@example.com", "Joe", "")),
            HttpStatus.CONFLICT);
        verify(userRepository, times(2)).saveAndFlush(any(UserEntity.class));
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
