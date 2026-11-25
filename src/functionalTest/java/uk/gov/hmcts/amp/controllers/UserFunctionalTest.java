package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.REQUESTING_USER_ID;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

class UserFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0004";
    private static final String EMAIL = "functional-test-users@amp.example.com";

    private final FunctionalClient http = new FunctionalClient();

    @Test
    void signing_in_should_make_a_new_user_a_user_rather_than_an_admin() throws Exception {
        var response = http.post("/session", "{\"objectId\":\"" + OID + "\",\"email\":\"" + EMAIL + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("role").asText()).isEqualTo("USER");
    }

    // Preview has no Entra tenant, so the chain refuses every admin request outright: 403 rather
    // than the 401 an environment with a tenant answers when a token is missing.
    @Test
    void reading_a_user_should_be_refused_without_an_admin_token() throws Exception {
        http.signIn(OID, EMAIL);

        assertThat(http.get("/admin/users/lookup/" + OID, REQUESTING_USER_ID, OID).statusCode()).isEqualTo(403);
    }

    @Test
    void listing_users_should_be_refused_without_an_admin_token() throws Exception {
        http.signIn(OID, EMAIL);

        assertThat(http.get("/admin/users/lookup", REQUESTING_USER_ID, OID).statusCode()).isEqualTo(403);
    }

    @Test
    void the_requesting_user_header_alone_should_not_open_an_admin_path() throws Exception {
        assertThat(http.get("/admin/users/lookup").statusCode()).isEqualTo(403);
    }
}
