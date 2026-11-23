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

    @Test
    void reading_a_user_should_be_refused_for_someone_who_is_not_an_admin() throws Exception {
        http.signIn(OID, EMAIL);

        assertThat(http.get("/admin/users/lookup/" + OID, REQUESTING_USER_ID, OID).statusCode()).isEqualTo(403);
    }

    @Test
    void listing_users_should_be_refused_for_someone_who_is_not_an_admin() throws Exception {
        http.signIn(OID, EMAIL);

        assertThat(http.get("/admin/users/lookup", REQUESTING_USER_ID, OID).statusCode()).isEqualTo(403);
    }

    @Test
    void listing_users_without_a_requesting_user_should_be_refused_with_400() throws Exception {
        assertThat(http.get("/admin/users/lookup").statusCode()).isEqualTo(400);
    }
}
