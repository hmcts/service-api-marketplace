package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AuthenticationResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.domain.Role;
import uk.gov.hmcts.cp.domain.User;
import uk.gov.hmcts.cp.domain.UserResponse;

import java.util.UUID;

// Stub implementation: every call returns the same fixed account and token. Nothing is stored, so
// registration never conflicts, any password is accepted, and any bearer token resolves. Request
// bodies are still validated, so the 400 responses in the OpenAPI spec are real.
@Slf4j
@Service
public class UserAccountService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_EXPIRED = "Session expired. Please sign in again.";
    private static final String STUB_TOKEN = "stub-bearer-token";

    private static final User STUB_USER = new User(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "Stub",
        "User",
        "stub.user@example.com",
        Role.CONSUMER);

    public AuthenticationResponse register(final RegisterRequest request) {
        log.info("Registration received: role={} - returning stub account", request.role().value());
        return new AuthenticationResponse(STUB_USER, STUB_TOKEN);
    }

    public AuthenticationResponse login(final LoginRequest request) {
        log.info("Sign-in received - returning stub account");
        return new AuthenticationResponse(STUB_USER, STUB_TOKEN);
    }

    public UserResponse currentUser(final String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }
        return new UserResponse(STUB_USER);
    }
}
