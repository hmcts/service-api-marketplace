package uk.gov.hmcts.amp.filters;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void every_response_should_carry_the_security_headers() throws Exception {
        MockHttpServletResponse response = filtered(new MockHttpServletRequest("GET", "/applications"));

        assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy"))
            .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void health_and_root_should_carry_them_too() throws Exception {
        assertThat(filtered(new MockHttpServletRequest("GET", "/health")).getHeaderNames())
            .containsAll(SecurityHeadersFilter.HEADERS.keySet());
        assertThat(filtered(new MockHttpServletRequest("GET", "/")).getHeaderNames())
            .containsAll(SecurityHeadersFilter.HEADERS.keySet());
    }

    @Test
    void headers_should_be_set_before_the_handler_runs_so_error_responses_keep_them() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/users"), response,
            (req, res) -> assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff"));
    }

    private MockHttpServletResponse filtered(final MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }
}
