package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.REQUESTING_USER_ID;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

/**
 * Every admin path is refused to anyone without an admin token. An environment with no Entra tenant
 * closes /admin/** outright (403); one with a tenant asks for a token (401). Either is a refusal.
 *
 * <p>An admin being served needs an environment with an Entra registration and a token for an
 * account holding amp_admin. The pipeline supplies the token as ADMIN_FUNCTIONAL_TOKEN; without it
 * those cases are skipped rather than failed.
 */
class AdminFunctionalTest {

    private static final String TOKEN = System.getenv("ADMIN_FUNCTIONAL_TOKEN");
    private static final String OID = "00000000-0000-0000-0000-0000000f0005";
    private static final String EMAIL = "functional-test-admin@amp.example.com";
    // Shaped like a JWT, but signed by nobody: it must never pass for an admin token.
    private static final String FORGED_TOKEN = "Bearer eyJhbGciOiJub25lIn0."
        + "eyJyb2xlcyI6WyJhbXBfYWRtaW4iXX0.";

    private final FunctionalClient http = new FunctionalClient();

    // Every admin path, including ones not built yet: Spring Security closes /admin/** before routing.
    static Stream<String> adminPaths() {
        return Stream.of("/admin/users", "/admin/users/1", "/admin/users/lookup", "/admin/users/lookup/" + OID,
            "/admin/applications", "/admin/applications/1");
    }

    @ParameterizedTest
    @MethodSource("adminPaths")
    void an_admin_path_should_be_refused_without_a_token(final String path) throws Exception {
        assertRefused(http.get(path));
    }

    @ParameterizedTest
    @MethodSource("adminPaths")
    void the_requesting_user_header_alone_should_not_open_an_admin_path(final String path) throws Exception {
        http.signIn(OID, EMAIL);

        assertRefused(http.get(path, REQUESTING_USER_ID, OID));
    }

    @ParameterizedTest
    @MethodSource("adminPaths")
    void an_admin_path_should_be_refused_for_a_token_nobody_signed(final String path) throws Exception {
        assertRefused(http.get(path, "Authorization", FORGED_TOKEN));
    }

    @Test
    void an_admin_should_be_served_the_registered_users() throws Exception {
        assumeAdminToken();
        http.signIn(OID, EMAIL);

        var response = http.get("/admin/users", "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
    }

    @Test
    void an_admin_should_be_served_the_applications() throws Exception {
        assumeAdminToken();

        var response = http.get("/admin/applications", "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
    }

    @Test
    void an_admin_should_be_served_the_support_lookup() throws Exception {
        assumeAdminToken();
        http.signIn(OID, EMAIL);

        var response = http.get("/admin/users/lookup/" + OID, "Authorization", "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("email").asText()).isEqualTo(EMAIL);
    }

    @Test
    void tampered_token_should_be_refused() throws Exception {
        assumeAdminToken();

        var response = http.get("/admin/users", "Authorization", "Bearer " + TOKEN + "x");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private static void assumeAdminToken() {
        assumeTrue(TOKEN != null && !TOKEN.isBlank(), "ADMIN_FUNCTIONAL_TOKEN is not set");
    }

    private static void assertRefused(final HttpResponse<String> response) {
        assertThat(response.statusCode()).isIn(401, 403);
    }
}
