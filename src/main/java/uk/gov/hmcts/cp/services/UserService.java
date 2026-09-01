package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<UserEntity> getUsers() {
        return userRepository.findAll();
    }

    public UserEntity validateUser(final int requestingUserId) {
        return userRepository.findById(requestingUserId)
            .orElseThrow(() -> {
                log.warn("Requesting user not found: {}", requestingUserId);
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requesting user not found.");
            });
    }
}
