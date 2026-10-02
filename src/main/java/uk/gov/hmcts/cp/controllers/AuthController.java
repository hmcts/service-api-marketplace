package uk.gov.hmcts.cp.controllers;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AuthResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.LogoutResponse;
import uk.gov.hmcts.cp.domain.MeResponse;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.services.AccountService;
import uk.gov.hmcts.cp.services.AuthRateLimiter;

/**
 * The account endpoints the frontend calls: register, sign in, sign out, and "who am I". Under /api
 * because that is where the frontend, and this service's OpenAPI spec, expect them - and so they
 * sit alongside, not on top of, the older header-authenticated endpoints that other clients use.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final AuthRateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
        @RequestBody final RegisterRequest request, final HttpServletRequest http) {
        limit(http);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @RequestBody final LoginRequest request, final HttpServletRequest http) {
        limit(http);
        return ResponseEntity.ok(accountService.login(request));
    }

    /**
     * Nothing to do server-side: the token lives in the browser, so signing out means the browser
     * discards it. Tokens are not revoked and stay valid until they expire. Kept because the
     * frontend calls it.
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout() {
        return ResponseEntity.ok(new LogoutResponse(true));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization) {
        return ResponseEntity.ok(accountService.currentUser(authorization));
    }

    private void limit(final HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            log.warn("Too many sign-in or registration attempts from one client");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many attempts. Please try again later.");
        }
    }
}
