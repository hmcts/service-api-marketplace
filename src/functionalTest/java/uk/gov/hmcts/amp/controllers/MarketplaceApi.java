package uk.gov.hmcts.amp.controllers;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    static void signIn(final String objectId, final String email) {
        anonymous()
            .body(Map.of("objectId", objectId, "email", email, "name", "Functional Test"))
            .post("/session")
            .then().statusCode(200);
    }

    /** Skips the calling test where the service has no Entra onboarding secret, as on preview and AAT. */
    static Response registerApplication(final String objectId, final String name) {
        final Response response = asUser(objectId)
            .body(Map.of("name", name, "apis", List.of()))
            .post("/applications");
        assumeFalse(response.statusCode() == 503,
            "Entra app registration is not configured here: " + response.asString());
        return response;
    }

    static void deleteApplicationsNamed(final String objectId, final String name) {
        final List<Map<String, Object>> owned = asUser(objectId).get("/applications").jsonPath().getList("$");
        owned.stream()
            .filter(application -> name.equals(application.get("name")))
            .forEach(application -> asUser(objectId).delete("/applications/" + application.get("clientId")));
    }
}
