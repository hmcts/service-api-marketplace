package uk.gov.hmcts.amp.filters;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenIdentityFilterTest {

    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String BOB = "bbbbbbbb-1111-2222-3333-444444444444";

    @Mock
    private JwtDecoder decoder;

    @InjectMocks
    private TokenIdentityFilter filter;

    @Test
    void verified_token_should_name_the_user_when_no_header_is_sent() throws Exception {
        when(decoder.decode("ada-token")).thenReturn(tokenFor(ADA));

        final HttpServletRequest passedOn = filtered(request("Bearer ada-token", null));

        assertThat(passedOn.getHeader("requestingUserId")).isEqualTo(ADA);
        assertThat(Collections.list(passedOn.getHeaders("requestingUserId"))).containsExactly(ADA);
        assertThat(Collections.list(passedOn.getHeaderNames())).contains("requestingUserId", "Authorization");
    }

    @Test
    void verified_token_should_win_over_a_header_naming_someone_else() throws Exception {
        when(decoder.decode("ada-token")).thenReturn(tokenFor(ADA));

        final HttpServletRequest passedOn = filtered(request("Bearer ada-token", BOB));

        assertThat(passedOn.getHeader("requestingUserId")).isEqualTo(ADA);
        assertThat(Collections.list(passedOn.getHeaders("requestingUserId"))).containsExactly(ADA);
        assertThat(Collections.list(passedOn.getHeaderNames()))
            .filteredOn("requestingUserId"::equalsIgnoreCase).hasSize(1);
    }

    @Test
    void the_tokens_user_should_be_matched_whatever_the_case_of_its_oid() throws Exception {
        when(decoder.decode("ada-token")).thenReturn(tokenFor(ADA.toUpperCase()));

        assertThat(filtered(request("Bearer ada-token", ADA)).getHeader("requestingUserId")).isEqualTo(ADA);
    }

    @Test
    void token_this_service_cannot_verify_should_leave_the_header_in_charge() throws Exception {
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("Jwt expired"));

        final MockHttpServletRequest sent = request("Bearer stale-token", ADA);

        assertThat(filtered(sent)).isSameAs(sent);
    }

    @Test
    void token_without_an_oid_should_leave_the_header_in_charge() throws Exception {
        when(decoder.decode("no-oid")).thenReturn(Jwt.withTokenValue("no-oid").header("alg", "RS256")
            .claim("sub", "someone").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());

        final MockHttpServletRequest sent = request("Bearer no-oid", ADA);

        assertThat(filtered(sent)).isSameAs(sent);
    }

    @Test
    void request_without_a_token_should_pass_through_untouched() throws Exception {
        final MockHttpServletRequest sent = request(null, ADA);

        assertThat(filtered(sent)).isSameAs(sent);
        verifyNoInteractions(decoder);
    }

    @Test
    void authorization_header_that_is_not_a_bearer_token_should_be_ignored() throws Exception {
        final MockHttpServletRequest sent = request("Basic dXNlcjpwYXNz", ADA);

        assertThat(filtered(sent)).isSameAs(sent);
        verifyNoInteractions(decoder);
    }

    @Test
    void other_headers_should_reach_the_endpoint_unchanged() throws Exception {
        when(decoder.decode("ada-token")).thenReturn(tokenFor(ADA));
        final MockHttpServletRequest sent = request("Bearer ada-token", null);
        sent.addHeader("X-Correlation-Id", "c0ffee");

        final HttpServletRequest passedOn = filtered(sent);

        assertThat(passedOn.getHeader("X-Correlation-Id")).isEqualTo("c0ffee");
        assertThat(passedOn.getHeader("Authorization")).isEqualTo("Bearer ada-token");
    }

    private HttpServletRequest filtered(final MockHttpServletRequest request) throws Exception {
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    private static MockHttpServletRequest request(final String authorization, final String requestingUserId) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/applications");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        if (requestingUserId != null) {
            request.addHeader("requestingUserId", requestingUserId);
        }
        return request;
    }

    private static Jwt tokenFor(final String oid) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claim("oid", oid)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
