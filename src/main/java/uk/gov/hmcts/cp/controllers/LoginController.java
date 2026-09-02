package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.UserService;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LoginController {

    private final UserService userService;

    /**
     * STUB. Looks the account up by email address and returns it; the supplied password is
     * accepted but never verified, so any password signs in as any known user. It exists so
     * the frontend can build its sign-in journey against a real endpoint.
     *
     * <p>Two things must change before this is used for anything real: the password has to
     * be checked against a stored hash, and the response should be the
     * AuthenticationResponse token described in the OpenAPI spec rather than the user
     * record. Until then an unknown email returns 404 from UserService, which also
     * distinguishes "no such account" from "wrong password" — the spec explicitly requires
     * that failures do not.
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody final LoginRequest request) {
        log.warn("Stub login: password is not verified");
        return ResponseEntity.ok(userService.getUser(request.getEmail()));
    }
}
