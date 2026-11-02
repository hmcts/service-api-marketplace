package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.SessionApi;
import uk.gov.hmcts.amp.openapi.model.User;
import uk.gov.hmcts.amp.services.UserService;

/** The web app posts the claims of someone Entra has signed in; they are taken as given. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SessionController implements SessionApi {

    private final UserService users;
    private final ResponseMapper mapper;

    @Override
    public User createSession(final SessionRequest request) {
        log.info("Start a session for the user Entra has signed in");
        return mapper.toUser(users.signIn(request));
    }
}
