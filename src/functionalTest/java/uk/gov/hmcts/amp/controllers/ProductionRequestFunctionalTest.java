package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.asUser;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.deleteApplicationsNamed;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.registerApplication;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.signIn;

/**
 * A request emails the requester and the review inboxes wherever Notify is configured. Preview and
 * AAT have no Notify key, so there the send fails and is only logged.
 */
class ProductionRequestFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0301";
    private static final String NAME = "Functional Prod App";

    @BeforeEach
    void signInAndClearUp() {
        signIn(OID, "functional-production@amp.example.com");
        clearUp();
    }

    @AfterEach
    void clearUp() {
        List<String> references = asUser(OID).get("/production-requests").jsonPath().getList("reference");
        references.forEach(reference -> asUser(OID).delete("/production-requests/" + reference));
        deleteApplicationsNamed(OID, NAME);
    }

    @Test
    void request_should_be_submitted_listed_read_and_deleted() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");

        var submitted = asUser(OID).body(requestFor(clientId)).post("/production-requests");
        var reference = submitted.jsonPath().getString("reference");
        var listed = asUser(OID).get("/production-requests");
        var detail = asUser(OID).get("/production-requests/" + reference);
        var deleted = asUser(OID).delete("/production-requests/" + reference);

        assertThat(submitted.statusCode()).isEqualTo(201);
        assertThat(reference).isNotBlank();
        assertThat(listed.jsonPath().getList("reference")).contains(reference);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.jsonPath().getString("clientId")).isEqualTo(clientId);
        assertThat(detail.jsonPath().getString("organisation")).isEqualTo("Functional Test Ltd");
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(asUser(OID).get("/production-requests/" + reference).statusCode()).isEqualTo(404);
    }

    @Test
    void second_request_for_the_same_application_should_be_refused_with_409() {
        var clientId = registerApplication(OID, NAME).jsonPath().getString("clientId");
        asUser(OID).body(requestFor(clientId)).post("/production-requests");

        var second = asUser(OID).body(requestFor(clientId)).post("/production-requests");

        assertThat(second.statusCode()).isEqualTo(409);
    }

    @Test
    void request_for_an_application_the_caller_does_not_own_should_be_refused_with_400() {
        var response = asUser(OID).body(requestFor("not-a-client-id")).post("/production-requests");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void an_incomplete_request_should_be_refused_with_400() {
        var incomplete = new HashMap<>(requestFor("not-a-client-id"));
        incomplete.remove("organisation");

        assertThat(asUser(OID).body(incomplete).post("/production-requests").statusCode()).isEqualTo(400);
    }

    private static Map<String, String> requestFor(final String clientId) {
        return Map.of(
            "clientId", clientId,
            "organisation", "Functional Test Ltd",
            "goLiveDate", LocalDate.now().plusMonths(1).toString(),
            "callVolume", "low",
            "dataRequest", "Court schedules for a functional test.",
            "proposedOutputs", "None: this request is made by an automated test."
        );
    }
}
