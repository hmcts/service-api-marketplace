package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.API_CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubNoApplications;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistration;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistrationAs;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.wireMockGraph;

/** The cap on applications per user, set low so the test stays short. */
@SpringBootTest(properties = {
    "applications.max-per-user=2",
    "entra.onboarding-client-id=a-test-onboarding-app",
    "entra.onboarding-client-secret=a-test-secret",
    "entra.resource-app-ids.slc=" + API_CLIENT_ID,
    "entra.max-attempts=2",
    "entra.retry-delay-millis=1"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockGraphInitialise.class})
class ApplicationLimitIntegrationTest {

    private static final String PATH = "/applications";
    private static final String OWNER_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void startClean() throws Exception {
        wireMockGraph().resetAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        signIn(OWNER_OID, "ada.lovelace@hmcts.net");
        signIn(OTHER_OID, "grace.hopper@hmcts.net");
    }

    @Test
    void the_allowance_should_count_down_to_the_limit() throws Exception {
        mockMvc.perform(get(PATH + "/allowance").header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxPerUser").value(2))
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.canCreate").value(true));

        fillTheAllowance();

        mockMvc.perform(get(PATH + "/allowance").header("requestingUserId", OWNER_OID))
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.canCreate").value(false));
    }

    @Test
    void one_over_the_limit_should_be_refused_before_entra() throws Exception {
        fillTheAllowance();

        mockMvc.perform(createNamed(OWNER_OID, "Third Application"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value(
                "You can have up to 2 applications. Delete an application you no longer need before you add another."));

        wireMockGraph().verify(2, postRequestedFor(urlPathEqualTo("/applications")));
    }

    @Test
    void deleting_one_should_free_a_place() throws Exception {
        fillTheAllowance();
        stubNoApplications();
        mockMvc.perform(delete(PATH + "/" + CLIENT_ID).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNoContent());

        stubRegistrationAs("33333333-3333-3333-3333-333333333333", "client-three");
        mockMvc.perform(createNamed(OWNER_OID, "Third Application")).andExpect(status().isCreated());
    }

    @Test
    void the_limit_should_be_per_user() throws Exception {
        fillTheAllowance();

        stubRegistrationAs("44444444-4444-4444-4444-444444444444", "client-other");
        mockMvc.perform(createNamed(OTHER_OID, "Case Viewer")).andExpect(status().isCreated());
    }

    private void fillTheAllowance() throws Exception {
        stubRegistration();
        mockMvc.perform(createNamed(OWNER_OID, "Case Viewer")).andExpect(status().isCreated());
        stubRegistrationAs("22222222-0000-0000-0000-000000000002", "client-two");
        mockMvc.perform(createNamed(OWNER_OID, "Court Lists")).andExpect(status().isCreated());
    }

    private RequestBuilder createNamed(final String oid, final String name) {
        return post(PATH).header("requestingUserId", oid)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}");
    }

    private void signIn(final String oid, final String email) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }
}