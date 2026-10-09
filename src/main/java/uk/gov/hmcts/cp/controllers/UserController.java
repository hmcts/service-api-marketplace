package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.EntraSignInRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.mappers.UserMapper;
import uk.gov.hmcts.cp.services.UserService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        log.info("Get users request");
        List<UserResponse> users = userService.getUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Called by the web app each time Entra External ID signs someone in. Idempotent: the same
     * person signing in again gets the same account back.
     */
    @PutMapping("/entra/{entraObjectId}")
    public ResponseEntity<UserResponse> signInWithEntra(
        @PathVariable final String entraObjectId, @Valid @RequestBody final EntraSignInRequest request) {
        return ResponseEntity.ok(userService.signInWithEntra(entraObjectId, request));
    }
}
