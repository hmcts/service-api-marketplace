package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.UserMapper;
import uk.gov.hmcts.cp.services.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserController userController;

    @Test
    void getting_users_should_return_200_with_mapped_user_list() {
        UserEntity entity = UserEntity.builder().build();
        UserResponse response = UserResponse.builder().build();
        when(userService.getUsers()).thenReturn(List.of(entity));
        when(userMapper.fromEntity(entity)).thenReturn(response);

        ResponseEntity<List<UserResponse>> result = userController.getUsers();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsExactly(response);
    }
}
