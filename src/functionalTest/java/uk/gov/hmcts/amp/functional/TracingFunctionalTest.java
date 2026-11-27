package uk.gov.hmcts.amp.functional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.functional.FunctionalClient.CORRELATION_ID;
import static uk.gov.hmcts.amp.functional.FunctionalClient.json;

class TracingFunctionalTest {

    private static final String SENT_ID = "c0ffee12-0000-0000-0000-0000000f0005";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final FunctionalClient http = new FunctionalClient();

    @Test
    void an_inbound_correlation_id_should_be_echoed_back() throws Exception {
        var response = http.get("/catalogue", CORRELATION_ID, SENT_ID);

        assertThat(response.headers().firstValue(CORRELATION_ID)).contains(SENT_ID);
    }

    @Test
    void request_without_a_correlation_id_should_be_given_one() throws Exception {
        var response = http.get("/catalogue");

        assertThat(response.headers().firstValue(CORRELATION_ID))
            .hasValueSatisfying(id -> assertThat(id).matches(UUID));
    }

    @Test
    void an_error_should_carry_the_correlation_id_as_its_trace_id() throws Exception {
        var response = http.get("/applications", CORRELATION_ID, SENT_ID);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("traceId").asText()).isEqualTo(SENT_ID);
    }
}
