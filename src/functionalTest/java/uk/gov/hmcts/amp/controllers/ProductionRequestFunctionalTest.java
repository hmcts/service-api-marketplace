package uk.gov.hmcts.amp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.REQUESTING_USER_ID;
import static uk.gov.hmcts.amp.controllers.FunctionalClient.json;

// Never reaches a real application, so nothing is recorded and no GOV.UK Notify email is sent.
class ProductionRequestFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0003";
    private static final String EMAIL = "functional-test-production@amp.example.com";
    private static final String NEVER_SIGNED_IN = "00000000-0000-0000-0000-0000000f0ff0";
    private static final String UNKNOWN_CLIENT_ID = "00000000-0000-0000-0000-0000000fffff";
    private static final String NO_SUCH_APPLICATION = "No application with client id " + UNKNOWN_CLIENT_ID + ".";

    private final FunctionalClient http = new FunctionalClient();

    @BeforeEach
    void signIn() throws Exception {
        http.signIn(OID, EMAIL);
    }

    @Test
    void listing_without_a_requesting_user_should_be_refused_with_400() throws Exception {
        var response = http.get("/production-requests");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("error").asText()).isEqualTo("requestingUserId is required.");
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        var response = http.get("/production-requests", REQUESTING_USER_ID, NEVER_SIGNED_IN);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).isEqualTo("Sign in again.");
    }

    @Test
    void signed_in_user_with_no_requests_should_list_none() throws Exception {
        var response = http.get("/production-requests", REQUESTING_USER_ID, OID);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).isArray()).isTrue();
        assertThat(json(response)).isEmpty();
    }

    @Test
    void an_unknown_reference_should_be_not_found() throws Exception {
        assertThat(http.get("/production-requests/PCR-NOSUCHRF", REQUESTING_USER_ID, OID).statusCode())
            .isEqualTo(404);
    }

    @Test
    void the_triage_answers_should_pass_validation_and_stop_only_at_the_unknown_application() throws Exception {
        var response = submit(triage());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("error").asText()).isEqualTo(NO_SUCH_APPLICATION);
    }

    @Test
    void data_leaving_the_requesters_own_team_should_need_an_explanation() throws Exception {
        var answers = triage();
        answers.put("dataRecipients", List.of("supplier"));
        answers.remove("dataRecipientsExplanation");

        assertRefused(submit(answers), "Explain who will receive the data and why.");
    }

    @Test
    void the_requesters_own_team_alone_should_need_no_explanation() throws Exception {
        var answers = triage();
        answers.put("dataRecipients", List.of("own-team"));
        answers.remove("dataRecipientsExplanation");

        assertRefused(submit(answers), NO_SUCH_APPLICATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "unsure"})
    void combining_the_data_should_need_a_description(final String dataCombined) throws Exception {
        var answers = triage();
        answers.put("dataCombined", dataCombined);
        answers.remove("dataCombinedDescription");

        assertRefused(submit(answers), "Describe the other data and what the combined data will be used for.");
    }

    @Test
    void an_organisation_type_outside_the_allowed_set_should_be_rejected() throws Exception {
        var answers = triage();
        answers.put("organisationType", "charity");

        assertRefused(submit(answers), "Organisation type must be hmcts, moj, other-government or external.");
    }

    @Test
    void data_recipient_outside_the_published_list_should_be_rejected() throws Exception {
        var answers = triage();
        answers.put("dataRecipients", List.of("friends"));

        assertRefused(submit(answers), "Data recipients must be from the published list.");
    }

    @Test
    void data_combined_answer_outside_the_allowed_set_should_be_rejected() throws Exception {
        var answers = triage();
        answers.put("dataCombined", "maybe");

        assertRefused(submit(answers), "Data combined must be no, yes or unsure.");
    }

    @Test
    void an_answer_past_two_thousand_characters_should_say_so() throws Exception {
        var answers = triage();
        answers.put("dataCombinedDescription", "x".repeat(2001));

        assertRefused(submit(answers), "Data combined description must be 2000 characters or fewer.");
    }

    private static Map<String, Object> triage() {
        final Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("clientId", UNKNOWN_CLIENT_ID);
        answers.put("organisation", "Functional Test Ltd");
        answers.put("goLiveDate", "2027-06-01");
        answers.put("callVolume", "low");
        answers.put("dataRequest", "Court schedules for listed hearings, to plan attendance.");
        answers.put("organisationType", "external");
        answers.put("dataRecipients", List.of("own-team", "supplier"));
        answers.put("dataRecipientsExplanation", "Our case management supplier hosts the system.");
        answers.put("dataCombined", "yes");
        answers.put("dataCombinedDescription", "Joined with our own case records to show upcoming hearings.");
        return answers;
    }

    private HttpResponse<String> submit(final Map<String, Object> answers) throws Exception {
        return http.post("/production-requests", new ObjectMapper().writeValueAsString(answers),
            REQUESTING_USER_ID, OID);
    }

    private static void assertRefused(final HttpResponse<String> response, final String message) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("error").asText()).contains(message);
    }
}
