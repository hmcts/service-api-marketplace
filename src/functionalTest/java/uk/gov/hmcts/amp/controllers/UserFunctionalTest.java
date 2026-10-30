package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.anonymous;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.asUser;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.signIn;

class UserFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0101";
    private static final String NEVER_SIGNED_IN = "00000000-0000-0000-0000-0000000f01ff";

    @BeforeEach
    void signInTheTestUser() {
        signIn(OID, "functional-users@amp.example.com");
    }

    @Test
    void the_user_list_should_include_the_signed_in_user() {
        var response = asUser(OID).get("/users");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("entraObjectId")).contains(OID);
    }

    @Test
    void user_should_be_found_by_object_id_and_by_row_id() {
        var byObjectId = asUser(OID).get("/users/" + OID);
        var rowId = byObjectId.jsonPath().getLong("id");

        var byRowId = asUser(OID).get("/users/" + rowId);

        assertThat(byObjectId.statusCode()).isEqualTo(200);
        assertThat(byObjectId.jsonPath().getString("userReference")).isEqualTo(String.format("user%03d", rowId));
        assertThat(byRowId.statusCode()).isEqualTo(200);
        assertThat(byRowId.jsonPath().getString("entraObjectId")).isEqualTo(OID);
    }

    @Test
    void an_unknown_user_should_be_404() {
        assertThat(asUser(OID).get("/users/" + NEVER_SIGNED_IN).statusCode()).isEqualTo(404);
    }

    @Test
    void call_without_the_requesting_user_should_be_refused_with_400() {
        assertThat(anonymous().get("/users").statusCode()).isEqualTo(400);
    }

    @Test
    void caller_who_never_signed_in_should_be_told_to_sign_in_again() {
        var response = asUser(NEVER_SIGNED_IN).get("/users");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.asString()).contains("Sign in again");
    }
}
