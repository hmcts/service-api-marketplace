package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedInCallerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

    private final SignedInCaller caller = new SignedInCaller();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void token_caller_should_be_named_by_the_oid_claim() {
        signIn(new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "RS256")
            .subject("someone-else").claim("oid", OID).build()));

        assertThat(caller.objectId()).isEqualTo(OID);
    }

    @Test
    void local_bypass_caller_should_be_named_by_its_principal() {
        signIn(UsernamePasswordAuthenticationToken.authenticated(OID, null, List.of()));

        assertThat(caller.objectId()).isEqualTo(OID);
    }

    @Test
    void anonymous_caller_should_be_nobody() {
        signIn(new AnonymousAuthenticationToken("key", "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(caller.objectIdOrNull()).isNull();
    }

    @Test
    void requiring_a_caller_when_there_is_none_should_ask_them_to_sign_in() {
        assertThatThrownBy(caller::objectId)
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static void signIn(final Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
