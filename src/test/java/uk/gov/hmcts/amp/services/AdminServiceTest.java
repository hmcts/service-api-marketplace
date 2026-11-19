package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AdminUserSummary;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository.UserApplicationCount;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final String ADMIN_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String USER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private AdminService service;

    @Test
    void an_admin_should_be_let_through() {
        final UserEntity admin = user(1L, "Ada", "Lovelace", UserRole.ADMIN);
        when(userService.requireSignedIn(ADMIN_OID)).thenReturn(admin);

        assertThat(service.requireAdmin(ADMIN_OID)).isSameAs(admin);
    }

    @Test
    void user_who_is_not_an_admin_should_be_forbidden() {
        when(userService.requireSignedIn(USER_OID)).thenReturn(user(2L, "Grace", "Hopper", UserRole.USER));

        assertThatThrownBy(() -> service.requireAdmin(USER_OID))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listing_should_be_refused_before_anything_is_read() {
        when(userService.requireSignedIn(USER_OID)).thenReturn(user(2L, "Grace", "Hopper", UserRole.USER));

        assertThatThrownBy(() -> service.listUsers(USER_OID)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(userRepository, applicationRepository);
    }

    @Test
    void listing_should_keep_the_repositorys_order_and_count_applications() {
        when(userService.requireSignedIn(ADMIN_OID)).thenReturn(user(1L, "Ada", "Lovelace", UserRole.ADMIN));
        final UserEntity grace = user(2L, "Grace", "Hopper", UserRole.USER);
        final UserEntity ada = user(1L, "Ada", "Lovelace", UserRole.ADMIN);
        when(userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE)).thenReturn(List.of(grace, ada));
        when(applicationRepository.countByUser()).thenReturn(List.of(count(2L, 3)));

        final List<AdminUserSummary> users = service.listUsers(ADMIN_OID);

        assertThat(users).extracting(AdminUserSummary::name).containsExactly("Grace Hopper", "Ada Lovelace");
        assertThat(users).extracting(AdminUserSummary::applicationCount).containsExactly(3L, 0L);
    }

    @Test
    void user_with_no_last_name_should_be_named_without_a_trailing_space() {
        when(userService.requireSignedIn(ADMIN_OID)).thenReturn(user(1L, "Ada", "Lovelace", UserRole.ADMIN));
        when(userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE))
            .thenReturn(List.of(user(3L, "Cher", "", UserRole.USER)));
        when(applicationRepository.countByUser()).thenReturn(List.of());

        assertThat(service.listUsers(ADMIN_OID).get(0).name()).isEqualTo("Cher");
    }

    private static UserEntity user(final long id, final String first, final String last, final UserRole role) {
        final UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(first.toLowerCase() + "@example.com");
        user.setStatus(UserEntity.ACTIVE);
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        user.setLastLoginAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        return user;
    }

    private static UserApplicationCount count(final long userId, final long total) {
        return new UserApplicationCount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}