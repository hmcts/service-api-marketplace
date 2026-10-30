package uk.gov.hmcts.amp.controllers;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.asUser;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.deleteApplicationsNamed;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.registerApplication;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.signIn;

class SubscriptionKeyFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0401";
    private static final String OTHER_OID = "00000000-0000-0000-0000-0000000f0402";
    private static final String NAME = "Functional Keys App";
    private static final String KEYS = "/apim/subscription-keys";

    @BeforeEach
    void signInAndClearUp() {
        signIn(OID, "functional-keys@amp.example.com");
        signIn(OTHER_OID, "functional-keys-other@amp.example.com");
        deleteApplicationsNamed(OID, NAME);
    }

    @AfterEach
    void clearUp() {
        // Deleting the application revokes its keys too
        deleteApplicationsNamed(OID, NAME);
    }

    @Test
    void key_should_be_issued_listed_read_and_deleted() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        var subscribed = subscribe(clientId, "slc");
        var name = subscribed.jsonPath().getString("subscriptionName");
        var listed = asUser(OID).get(KEYS);
        var values = asUser(OID).get(KEYS + "/" + name + "/values");
        var deleted = asUser(OID).delete(KEYS + "/" + name);

        assertThat(subscribed.statusCode()).isEqualTo(201);
        assertThat(name).startsWith("amp-user").endsWith("-functional-keys-app-slc");
        assertThat(subscribed.jsonPath().getString("subscriptionKey")).isNotBlank();
        assertThat(listed.jsonPath().getList("name")).contains(name);
        assertThat(values.jsonPath().getString("primaryKey"))
            .isEqualTo(subscribed.jsonPath().getString("subscriptionKey"));
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(asUser(OID).get(KEYS).jsonPath().getList("name")).doesNotContain(name);
    }

    @Test
    void second_key_for_the_same_api_should_be_refused_with_409() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");
        subscribe(clientId, "slc");

        assertThat(subscribe(clientId, "slc").statusCode()).isEqualTo(409);
    }

    @Test
    void key_for_an_unknown_api_should_be_refused_with_400() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        assertThat(subscribe(clientId, "zzz").statusCode()).isEqualTo(400);
    }

    @Test
    void another_users_key_should_be_404() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");
        var name = subscribe(clientId, "slc").jsonPath().getString("subscriptionName");

        assertThat(asUser(OTHER_OID).get(KEYS + "/" + name + "/values").statusCode()).isEqualTo(404);
    }

    private static Response subscribe(final String clientId, final String shortCode) {
        return asUser(OID).body(Map.of("clientId", clientId, "shortCode", shortCode)).post(KEYS);
    }
}
