package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

/**
 * An admin being served, which needs an environment with an Entra registration and a token for an
 * account holding amp_admin. The pipeline supplies the token as ADMIN_FUNCTIONAL_TOKEN; without it
 * these are skipped rather than failed.
 */
class AdminFunctionalTest {

    private static final String TOKEN = System.getenv("ADMIN_FUNCTIONAL_TOKEN");
    private static final String OID = "00000000-0000-0000-0000-0000000f0005";
    private static final String EMAIL = "functional-test-admin@amp.example.com";

    private final FunctionalClient http = new FunctionalClient();

    @BeforeEach
    void needsAnAdminToken() {
        assumeTrue(TOKEN != null && !TOKEN.isBlank(), "ADMIN_FUNCTIONAL_TOKEN is not set");
    }

    @Test
    void an_admin_should_be_served_the_registered_users() throws Exception {
        http.signIn(OID, EMAIL);

        var response = http.get("/admin/users", "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
    }

    @Test
    void an_admin_should_be_served_the_applications() throws Exception {
        var response = http.get("/admin/applications", "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
    }

    @Test
    void an_admin_should_be_served_the_support_lookup() throws Exception {
        http.signIn(OID, EMAIL);

        var response = http.get("/admin/users/lookup/" + OID, "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("email").asText()).isEqualTo(EMAIL);
    }

    @Test
    void tampered_token_should_be_refused() throws Exception {
        var response = http.get("/admin/users", "Authorization", "Bearer " + TOKEN + "x");

        assertThat(response.statusCode()).isEqualTo(401);
    }
}