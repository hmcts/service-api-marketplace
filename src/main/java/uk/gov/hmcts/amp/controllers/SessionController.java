package uk.gov.hmcts.amp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

/** The web app posts the claims of someone Entra has signed in; they are taken as given. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final UserService users;

    @PostMapping("/session")
    public UserResponse startSession(@Valid @RequestBody final SessionRequest request) {
        log.info("Start a session for the user Entra has signed in");
        return users.signIn(request);
    }
}
