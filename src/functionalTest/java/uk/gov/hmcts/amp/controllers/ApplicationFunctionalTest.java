package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.asUser;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.deleteApplicationsNamed;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.registerApplication;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.signIn;

class ApplicationFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0201";
    private static final String OTHER_OID = "00000000-0000-0000-0000-0000000f0202";
    private static final String NAME = "Functional Test App";

    @BeforeEach
    void signInAndClearUp() {
        signIn(OID, "functional-applications@amp.example.com");
        signIn(OTHER_OID, "functional-applications-other@amp.example.com");
        deleteApplicationsNamed(OID, NAME);
    }

    @AfterEach
    void clearUp() {
        deleteApplicationsNamed(OID, NAME);
    }

    @Test
    void registering_should_show_the_secret_once_and_list_the_application() {
        var registered = registerApplication(OID, NAME);
        var clientId = registered.jsonPath().getString("clientId");

        var listed = asUser(OID).get("/applications");
        var readBack = asUser(OID).get("/applications/" + clientId);

        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.jsonPath().getString("clientSecret")).isNotBlank();
        assertThat(registered.jsonPath().getString("appname")).isEqualTo("functional-test-app");
        assertThat(listed.jsonPath().getList("clientId")).contains(clientId);
        assertThat(readBack.statusCode()).isEqualTo(200);
        assertThat(readBack.jsonPath().getString("clientSecret")).isNull();
    }

    @Test
    void deleted_application_should_be_gone() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        var deleted = asUser(OID).delete("/applications/" + clientId);

        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(asUser(OID).get("/applications/" + clientId).statusCode()).isEqualTo(404);
    }

    @Test
    void second_application_with_the_same_name_should_be_refused_with_409() {
        registerApplication(OID, NAME);

        assertThat(registerApplication(OID, NAME).statusCode()).isEqualTo(409);
    }

    @Test
    void name_outside_the_allowed_pattern_should_be_refused_with_400() {
        var response = asUser(OID).body(Map.of("name", "Short", "apis", List.of())).post("/applications");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void another_users_application_should_be_404() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        assertThat(asUser(OTHER_OID).get("/applications/" + clientId).statusCode()).isEqualTo(404);
    }

    @Test
    void access_should_be_granted_to_a_known_api_and_refused_for_an_unknown_one() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        var known = asUser(OID).body(Map.of("shortCode", "slc")).post("/applications/" + clientId + "/roles");
        var unknown = asUser(OID).body(Map.of("shortCode", "zzz")).post("/applications/" + clientId + "/roles");

        assertThat(known.statusCode()).isEqualTo(204);
        assertThat(unknown.statusCode()).isEqualTo(400);
    }
}
