package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.REQUESTING_USER_ID;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

// A key belongs to the user whose prefix starts its name; another user's is refused before APIM is asked.
class SubscriptionKeyFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0007";
    private static final String EMAIL = "functional-test-keys@amp.example.com";
    private static final String SOMEONE_ELSES = "amp-user999-not-mine-slc";
    private static final String UNKNOWN_CLIENT_ID = "00000000-0000-0000-0000-0000000ffffd";

    private final FunctionalClient http = new FunctionalClient();

    @BeforeEach
    void signIn() throws Exception {
        http.signIn(OID, EMAIL);
    }

    @Test
    void reading_another_users_key_should_be_not_found() throws Exception {
        assertNotFound(http.get("/apim/subscription-keys/" + SOMEONE_ELSES + "/values", REQUESTING_USER_ID, OID));
    }

    @Test
    void deleting_another_users_key_should_be_not_found() throws Exception {
        assertNotFound(http.delete("/apim/subscription-keys/" + SOMEONE_ELSES, REQUESTING_USER_ID, OID));
    }

    @Test
    void subscribing_an_application_the_user_does_not_own_should_be_not_found() throws Exception {
        var response = http.post("/apim/subscription-keys",
            "{\"clientId\":\"" + UNKNOWN_CLIENT_ID + "\",\"shortCode\":\"slc\"}", REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText())
            .isEqualTo("No application with client id " + UNKNOWN_CLIENT_ID + ".");
    }

    @Test
    void subscribing_without_a_requesting_user_should_be_refused_with_400() throws Exception {
        var response = http.post("/apim/subscription-keys",
            "{\"clientId\":\"" + UNKNOWN_CLIENT_ID + "\",\"shortCode\":\"slc\"}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private static void assertNotFound(final HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).isEqualTo("No subscription named " + SOMEONE_ELSES + ".");
    }
}
