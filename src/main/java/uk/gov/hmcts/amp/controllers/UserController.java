package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.UsersApi;
import uk.gov.hmcts.amp.openapi.model.User;
import uk.gov.hmcts.amp.services.UserService;

import java.util.List;

/** Who has signed in. Read only: a user appears here by using the service, never by being added. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {

    private final UserService users;
    private final ResponseMapper mapper;

    @Override
    public List<User> listUsers(final String requestingUserId) {
        log.info("List marketplace users");
        return mapper.toUsers(users.list(requestingUserId));
    }

    @Override
    public User getUser(final String requestingUserId, final String userId) {
        log.info("Read marketplace user {}", userId);
        return mapper.toUser(users.get(requestingUserId, userId));
    }
}
