package uk.gov.hmcts.amp.controllers;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Calls the deployed service at TEST_URL, the way the web app does. */
final class MarketplaceApi {

    static final String TEST_URL = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private MarketplaceApi() {
    }

    static RequestSpecification anonymous() {
        return RestAssured.given().baseUri(TEST_URL).relaxedHTTPSValidation().contentType(ContentType.JSON);
    }

    static RequestSpecification asUser(final String objectId) {
        return anonymous().header(REQUESTING_USER_ID, objectId);
    }

    /** Creates the user on the first run and updates them after, so every run starts from the same row. */
    static void signIn(final String objectId, final String email) {
        anonymous()
            .body(Map.of("objectId", objectId, "email", email, "name", "Functional Test"))
            .post("/session")
            .then().statusCode(200);
    }

    /**
     * Registers an application, or skips the calling test where this environment cannot: the service
     * answers 503 when it has no Entra onboarding secret, as preview and AAT have none yet.
     */
    static Response registerApplication(final String objectId, final String name) {
        final Response response = asUser(objectId)
            .body(Map.of("name", name, "apis", List.of()))
            .post("/applications");
        assumeFalse(response.statusCode() == 503,
            "Entra app registration is not configured here: " + response.asString());
        return response;
    }

    /** Removes what an earlier, interrupted run left behind, so a fixed name can be registered again. */
    static void deleteApplicationsNamed(final String objectId, final String name) {
        final List<Map<String, Object>> owned = asUser(objectId).get("/applications").jsonPath().getList("$");
        owned.stream()
            .filter(application -> name.equals(application.get("name")))
            .forEach(application -> asUser(objectId).delete("/applications/" + application.get("clientId")));
    }
}
