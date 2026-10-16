package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

import java.util.List;

/** Who has signed in. Read only: a user appears here by using the service, never by being added. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private final UserService users;

    @GetMapping
    public List<UserResponse> listUsers(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId) {
        log.info("List marketplace users");
        return users.list(requestingUserId);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                @PathVariable final String userId) {
        log.info("Read marketplace user {}", userId);
        return users.get(requestingUserId, userId);
    }
}
