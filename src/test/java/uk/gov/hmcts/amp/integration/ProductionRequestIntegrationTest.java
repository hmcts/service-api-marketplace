package uk.gov.hmcts.amp.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.API_CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistration;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistrationAs;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.wireMockGraph;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.SEND_EMAIL;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubAccepted;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubRefused;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.wireMockNotify;

/** /production-requests end to end, with Entra standing in. */
@SpringBootTest(properties = {
    "entra.onboarding-client-id=a-test-onboarding-app",
    "entra.onboarding-client-secret=a-test-secret",
    "entra.api-client-id=" + API_CLIENT_ID,
    "entra.max-attempts=2",
    "entra.retry-delay-millis=1",
    "notify.api-key=amptest-00000000-0000-0000-0000-000000000000-11111111-1111-1111-1111-111111111111"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockGraphInitialise.class,
    WireMockNotifyInitialise.class})
class ProductionRequestIntegrationTest {

    private static final String PATH = "/production-requests";
    private static final String OWNER_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductionRequestRepository productionRequests;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void startClean() throws Exception {
        wireMockGraph().resetAll();
        wireMockNotify().resetAll();
        stubAccepted();
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        signIn(OWNER_OID, "ada.lovelace@hmcts.net");
        signIn(OTHER_OID, "grace.hopper@hmcts.net");
    }

    @Test
    void submitting_should_return_a_reference_and_record_the_request() throws Exception {
        createApplication(OWNER_OID);

        String body = mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.submittedAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.reference")).startsWith("PCR-");
        assertThat(productionRequests.count()).isEqualTo(1);
    }

    @Test
    void submitting_should_email_the_requester_with_the_reference_it_minted() throws Exception {
        createApplication(OWNER_OID);

        String reference = reference(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)));

        wireMockNotify().verify(postRequestedFor(urlPathEqualTo(SEND_EMAIL))
            .withRequestBody(equalToJson("""
                {"email_address":"ada.lovelace@hmcts.net","reference":"%s",
                 "personalisation":{"consumer_name":"Ada Lovelace","application_name":"Case Viewer"}}"""
                .formatted(reference), true, true)));
    }

    @Test
    void notify_failing_should_still_leave_the_request_recorded() throws Exception {
        createApplication(OWNER_OID);
        stubRefused(400, "email_address Not a valid email address");

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)))
            .andExpect(status().isCreated());

        assertThat(productionRequests.count()).isEqualTo(1);
    }

    @Test
    void listing_should_show_only_the_callers_own_requests_newest_first() throws Exception {
        createApplication(OWNER_OID);
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID))).andExpect(status().isCreated());

        mockMvc.perform(get(PATH).header("requestingUserId", OTHER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    void naming_someone_elses_application_should_be_rejected_and_stored_nowhere() throws Exception {
        createApplication(OWNER_OID);

        mockMvc.perform(post(PATH).header("requestingUserId", OTHER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)))
            .andExpect(status().isBadRequest());

        assertThat(productionRequests.count()).isZero();
    }

    @Test
    void an_unknown_client_id_should_be_rejected_rather_than_pointing_at_nothing() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(form("no-such-client")))
            .andExpect(status().isBadRequest());

        assertThat(productionRequests.count()).isZero();
    }

    @Test
    void missing_data_request_should_be_rejected_by_the_server() throws Exception {
        createApplication(OWNER_OID);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form(CLIENT_ID).replace("Hearing outcomes for listed cases.", "")))
            .andExpect(status().isBadRequest());

        assertThat(productionRequests.count()).isZero();
    }

    @Test
    void missing_proposed_outputs_should_be_rejected_by_the_server() throws Exception {
        createApplication(OWNER_OID);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form(CLIENT_ID).replace("A weekly outcomes report.", "")))
            .andExpect(status().isBadRequest());

        assertThat(productionRequests.count()).isZero();
    }

    @Test
    void an_answer_outside_the_allowed_set_should_be_rejected() throws Exception {
        createApplication(OWNER_OID);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(form(CLIENT_ID).replace("\"low\"", "\"enormous\"")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        mockMvc.perform(get(PATH).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    @Test
    void two_requests_should_get_different_references() throws Exception {
        createApplication(OWNER_OID);
        String first = reference(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)));
        String second = reference(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void reading_one_request_should_return_the_answers_the_list_leaves_out() throws Exception {
        createApplication(OWNER_OID);
        String reference = reference(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)));

        mockMvc.perform(get(PATH + "/" + reference).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reference").value(reference))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.applicationName").value("Case Viewer"))
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$.organisation").value("Example Legal Ltd"))
            .andExpect(jsonPath("$.goLiveDate").value("2027-03-27"))
            .andExpect(jsonPath("$.callVolume").value("low"))
            .andExpect(jsonPath("$.dataRequest").value("Hearing outcomes for listed cases."))
            .andExpect(jsonPath("$.proposedOutputs").value("A weekly outcomes report."));
    }

    @Test
    void the_list_should_not_carry_the_answers_only_the_detail_does() throws Exception {
        createApplication(OWNER_OID);
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID))).andExpect(status().isCreated());

        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].useCase").doesNotExist())
            .andExpect(jsonPath("$[0].organisation").doesNotExist());
    }

    @Test
    void reading_another_users_request_should_be_not_found() throws Exception {
        createApplication(OWNER_OID);
        String reference = reference(post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content(form(CLIENT_ID)));

        mockMvc.perform(get(PATH + "/" + reference).header("requestingUserId", OTHER_OID))
            .andExpect(status().isNotFound());
    }

    @Test
    void reading_a_reference_that_does_not_exist_should_be_not_found() throws Exception {
        mockMvc.perform(get(PATH + "/PCR-NOSUCH99").header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());
    }

    private String reference(final org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return JsonPath.read(mockMvc.perform(request).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.reference");
    }

    private String form(final String clientId) {
        return """
            {"clientId":"%s","organisation":"Example Legal Ltd","goLiveDate":"2027-03-27",
             "callVolume":"low","dataRequest":"Hearing outcomes for listed cases.",
             "proposedOutputs":"A weekly outcomes report."}""".formatted(clientId);
    }

    private void signIn(final String oid, final String email) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }

    private void createApplication(final String oid) throws Exception {
        stubRegistration();
        mockMvc.perform(post("/applications").header("requestingUserId", oid)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isCreated());
        stubRegistrationAs("77777777-7777-7777-7777-777777777777", "client-other");
    }
}
