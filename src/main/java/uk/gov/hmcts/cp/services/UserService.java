package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.UserMapper;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse validateUser(final int requestingUserId) {
        UserEntity user = userRepository.findById(requestingUserId)
            .orElseThrow(() -> {
                log.warn("Requesting user not found: {}", requestingUserId);
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requesting user not found.");
            });
        return userMapper.fromEntity(user);
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream().map(userMapper::fromEntity).toList();
    }

    public UserResponse getUser(final String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail.toLowerCase(Locale.UK))
            .orElseThrow(() -> {
                // Deliberately does not echo the address back, matching the spec's note
                // that responses must not confirm whether an email is registered.
                log.warn("User not found for the requested email address");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
            });
        return userMapper.fromEntity(user);
    }
}
