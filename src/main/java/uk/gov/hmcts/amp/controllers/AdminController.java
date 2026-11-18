package uk.gov.hmcts.amp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * The first endpoint behind the admin gate, and for now the only one: it answers with the identity
 * the service read out of the token it verified, so an environment can be checked end to end before
 * any admin screen exists.
 *
 * <p>Deliberately not in the OpenAPI spec yet. The admin operations that follow - recording a
 * production access decision, inviting the consumer to generate - belong in the spec and will arrive
 * as generated interfaces like every other controller here.
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/whoami")
    public AdminIdentity whoami(@AuthenticationPrincipal final Jwt token) {
        log.info("Admin identity read for {}", token.getClaimAsString("oid"));
        return new AdminIdentity(
            token.getClaimAsString("oid"),
            Objects.requireNonNullElse(token.getClaimAsStringList("roles"), List.of()));
    }

    /** Nothing from the token but who they are and what they may do; no name, no email address. */
    public record AdminIdentity(String objectId, List<String> roles) {
    }
}
