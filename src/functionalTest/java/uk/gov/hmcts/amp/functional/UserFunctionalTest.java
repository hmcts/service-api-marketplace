package uk.gov.hmcts.amp.functional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.functional.FunctionalClient.json;

class UserFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0004";
    private static final String EMAIL = "functional-test-users@amp.example.com";

    private final FunctionalClient http = new FunctionalClient();

    @Test
    void signing_in_should_return_the_user() throws Exception {
        var response = http.post("/session", "{\"objectId\":\"" + OID + "\",\"email\":\"" + EMAIL + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("email").asText()).isEqualTo(EMAIL);
    }
}
