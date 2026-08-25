package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.AuthenticationResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.LogoutResponse;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.UserAccountService;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthenticationController {

    private final UserAccountService userAccountService;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody final RegisterRequest request) {
        log.info("Registration received: role={}", request.role().value());
        return ResponseEntity.status(HttpStatus.CREATED).body(userAccountService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody final LoginRequest request) {
        log.info("Sign-in received");
        return ok(userAccountService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout() {
        log.info("Sign-out received - bearer tokens are not revoked server-side");
        return ok(new LogoutResponse(true));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization) {
        return ok(userAccountService.currentUser(authorization));
    }
}
