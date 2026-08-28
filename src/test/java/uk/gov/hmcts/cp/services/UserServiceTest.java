package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static final UserEntity USER = UserEntity.builder().build();

    @Test
    void getting_users_should_return_all_users_from_repository() {
        when(userRepository.findAll()).thenReturn(List.of(USER));

        assertThat(userService.getUsers()).containsExactly(USER);
    }

    @Test
    void validating_a_known_user_should_return_the_user_entity() {
        when(userRepository.findById(1)).thenReturn(Optional.of(USER));

        assertThat(userService.validateUser(1)).isEqualTo(USER);
    }

    @Test
    void validating_an_unknown_user_should_throw_401() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.validateUser(1))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
