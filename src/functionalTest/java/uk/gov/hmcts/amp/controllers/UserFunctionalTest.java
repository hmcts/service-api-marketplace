package uk.gov.hmcts.amp.controllers;

import com.fasterxml.jackson.databind.JsonNode;
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
    void reading_a_user_by_their_entra_object_id_should_carry_their_role() throws Exception {
        http.signIn(OID, EMAIL);

        var response = http.get("/users/" + OID, REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("entraObjectId").asText()).isEqualTo(OID);
        assertThat(json(response).path("role").asText()).isEqualTo("USER");
    }

    @Test
    void listing_users_should_give_everyone_a_role() throws Exception {
        http.signIn(OID, EMAIL);

        var response = http.get("/users", REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response)).isNotEmpty();
        for (JsonNode user : json(response)) {
            assertThat(user.path("role").asText()).isIn("USER", "ADMIN");
        }
    }

    @Test
    void listing_users_without_a_requesting_user_should_be_refused_with_400() throws Exception {
        assertThat(http.get("/users").statusCode()).isEqualTo(400);
    }
}
