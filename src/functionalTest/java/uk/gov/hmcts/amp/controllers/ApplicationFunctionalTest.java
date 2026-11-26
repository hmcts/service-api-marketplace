package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.REQUESTING_USER_ID;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

// Every case is refused before Entra or APIM is called, so nothing is registered anywhere.
class ApplicationFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0006";
    private static final String EMAIL = "functional-test-applications@amp.example.com";
    private static final String NEVER_SIGNED_IN = "00000000-0000-0000-0000-0000000f0ff1";
    private static final String UNKNOWN_CLIENT_ID = "00000000-0000-0000-0000-0000000ffffe";
    private static final String NO_SUCH_APPLICATION = "No application with client id " + UNKNOWN_CLIENT_ID + ".";

    private final FunctionalClient http = new FunctionalClient();

    @BeforeEach
    void signIn() throws Exception {
        http.signIn(OID, EMAIL);
    }

    @Test
    void listing_without_a_requesting_user_should_be_refused_with_400() throws Exception {
        assertThat(http.get("/applications").statusCode()).isEqualTo(400);
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        var response = http.get("/applications", REQUESTING_USER_ID, NEVER_SIGNED_IN);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).isEqualTo("Sign in again.");
    }

    @Test
    void signed_in_user_with_no_applications_should_list_none() throws Exception {
        var response = http.get("/applications", REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
        assertThat(json(response)).isEmpty();
    }

    @Test
    void reading_an_application_the_user_does_not_own_should_be_not_found() throws Exception {
        assertNotFound(http.get("/applications/" + UNKNOWN_CLIENT_ID, REQUESTING_USER_ID, OID));
    }

    @Test
    void deleting_an_application_the_user_does_not_own_should_be_not_found() throws Exception {
        assertNotFound(http.delete("/applications/" + UNKNOWN_CLIENT_ID, REQUESTING_USER_ID, OID));
    }

    @Test
    void granting_a_role_on_an_application_the_user_does_not_own_should_be_not_found() throws Exception {
        assertNotFound(http.post("/applications/" + UNKNOWN_CLIENT_ID + "/roles", "{\"shortCode\":\"slc\"}",
            REQUESTING_USER_ID, OID));
    }

    @Test
    void name_that_is_too_short_should_be_refused_with_400() throws Exception {
        var response = http.post("/applications", "{\"name\":\"Short\",\"apis\":[\"slc\"]}", REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void an_api_nobody_publishes_should_be_refused_before_anything_is_registered() throws Exception {
        var response = http.post("/applications", "{\"name\":\"Functional Test App\",\"apis\":[\"nope\"]}",
            REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("error").asText()).isEqualTo("No API with short code nope.");
    }

    private static void assertNotFound(final HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).isEqualTo(NO_SUCH_APPLICATION);
    }
}
