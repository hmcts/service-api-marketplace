package uk.gov.hmcts.amp.integration;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.SEND_EMAIL;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubAccepted;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubRefused;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.wireMockNotify;

/** The temporary /notify-test endpoint, against the Notify stand-in. */
@SpringBootTest(properties =
    "notify.api-key=amptest-00000000-0000-0000-0000-000000000000-11111111-1111-1111-1111-111111111111")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockNotifyInitialise.class})
class NotifyTestEndpointIntegrationTest {

    private static final String PATH = "/notify-test";
    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";

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
        wireMockNotify().resetAll();
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        signIn(OID, "ada.lovelace@hmcts.net");
        stubAccepted();
    }

    @Test
    void an_empty_body_should_still_send_with_the_example_values() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationName").value("Case Viewer"))
            .andExpect(jsonPath("$.reference").value("PCR-TESTSEND"));

        wireMockNotify().verify(postRequestedFor(urlPathEqualTo(SEND_EMAIL)));
    }

    @Test
    void what_the_body_supplies_should_reach_notify() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"consumerName":"Grace Hopper","applicationName":"Rota Viewer",
                     "apis":["Court Schedule"],"reference":"PCR-ABC123"}"""))
            .andExpect(status().isOk());

        wireMockNotify().verify(postRequestedFor(urlPathEqualTo(SEND_EMAIL))
            .withRequestBody(equalToJson("""
                {"email_address":"ada.lovelace@hmcts.net","reference":"PCR-ABC123",
                 "personalisation":{"consumer_name":"Grace Hopper","application_name":"Rota Viewer",
                 "api_list":["Court Schedule"]}}""", true, true)));
    }

    @Test
    void it_should_send_to_the_caller_rather_than_anywhere_the_body_names() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"somebody.else@example.com\"}"))
            .andExpect(status().isOk());

        wireMockNotify().verify(postRequestedFor(urlPathEqualTo(SEND_EMAIL))
            .withRequestBody(equalToJson("{\"email_address\":\"ada.lovelace@hmcts.net\"}", true, true)));
    }

    @Test
    void notify_refusing_should_surface_rather_than_look_like_a_send() throws Exception {
        stubRefused(400, "email_address Not a valid email address");

        mockMvc.perform(post(PATH).header("requestingUserId", OID))
            .andExpect(status().isBadGateway());
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    private void signIn(final String oid, final String email) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }
}
