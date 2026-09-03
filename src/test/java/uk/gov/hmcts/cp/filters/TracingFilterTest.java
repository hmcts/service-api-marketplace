package uk.gov.hmcts.cp.filters;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TracingFilterTest {

    private final TracingFilter filter = new TracingFilter();

    @Test
    void filtering_request_with_correlation_id_should_propagate_it_to_response() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "test-correlation-id");

        filter.doFilterInternal(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("test-correlation-id");
    }

    @Test
    void filtering_request_without_correlation_id_should_generate_a_uuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> { });

        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull();
        assertThatNoException().isThrownBy(() -> UUID.fromString(correlationId));
    }

    @Test
    void filtering_request_with_blank_correlation_id_should_generate_a_uuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "   ");

        filter.doFilterInternal(request, response, (req, res) -> { });

        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull();
        assertThatNoException().isThrownBy(() -> UUID.fromString(correlationId));
    }

    @Test
    void should_not_filter_root_path() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void should_not_filter_health_endpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void should_filter_api_endpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/subscriptions");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
