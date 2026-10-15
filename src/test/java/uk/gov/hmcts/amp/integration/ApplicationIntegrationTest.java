package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.API_CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.OBJECT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubApplicationDelete;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubApplicationList;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistration;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistrationAs;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.wireMockGraph;

/** /applications against WireMock, with the ownership boundary the -new endpoints do not have. */
@SpringBootTest(properties = {
    "entra.onboarding-client-id=a-test-onboarding-app",
    "entra.onboarding-client-secret=a-test-secret",
    "entra.api-client-id=" + API_CLIENT_ID,
    "entra.max-attempts=2",
    "entra.retry-delay-millis=1"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockGraphInitialise.class})
class ApplicationIntegrationTest {

    private static final String PATH = "/applications";
    private static final String OWNER_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startClean() throws Exception {
        wireMockGraph().resetAll();
        jdbc.update("delete from application_api");
        jdbc.update("delete from application");
        jdbc.update("delete from marketplace_user");
        signIn(OWNER_OID, "ada.lovelace@hmcts.net");
        signIn(OTHER_OID, "grace.hopper@hmcts.net");
    }

    @Test
    void creating_should_return_the_secret_once_and_record_the_application() throws Exception {
        stubRegistration();

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$.name").value("Case Viewer"))
            .andExpect(jsonPath("$.appname").value("case-viewer"))
            .andExpect(jsonPath("$.secretHint").value("the"))
            .andExpect(jsonPath("$.clientSecret").value("the-secret-shown-once"));

        wireMockGraph().verify(postRequestedFor(urlPathEqualTo("/applications")));
    }

    @Test
    void listing_should_show_only_the_callers_own_applications() throws Exception {
        create(OWNER_OID, "Case Viewer");

        mockMvc.perform(get(PATH).header("requestingUserId", OTHER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].appname").value("case-viewer"))
            .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void reading_someone_elses_application_should_be_404() throws Exception {
        create(OWNER_OID, "Case Viewer");

        mockMvc.perform(get(PATH + "/" + CLIENT_ID).header("requestingUserId", OTHER_OID))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleting_someone_elses_application_should_be_not_found_and_change_nothing() throws Exception {
        create(OWNER_OID, "Case Viewer");
        stubApplicationDelete();

        mockMvc.perform(delete(PATH + "/" + CLIENT_ID).header("requestingUserId", OTHER_OID))
            .andExpect(status().isNotFound());

        wireMockGraph().verify(0, deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(jsonPath("$[0].appname").value("case-viewer"));
    }

    @Test
    void deleting_your_own_application_should_remove_it_from_entra_and_the_table() throws Exception {
        create(OWNER_OID, "Case Viewer");
        stubApplicationList();
        stubApplicationDelete();

        mockMvc.perform(delete(PATH + "/" + CLIENT_ID).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNoContent());

        wireMockGraph().verify(deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void the_same_name_twice_should_conflict_without_creating_anything() throws Exception {
        create(OWNER_OID, "Case Viewer");

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isConflict());

        wireMockGraph().verify(1, postRequestedFor(urlPathEqualTo("/applications")));
    }

    @Test
    void two_users_should_be_able_to_use_the_same_name() throws Exception {
        create(OWNER_OID, "Case Viewer");
        stubRegistrationAs("77777777-7777-7777-7777-777777777777", "client-other");

        mockMvc.perform(post(PATH).header("requestingUserId", OTHER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appname").value("case-viewer"));

        mockMvc.perform(get(PATH).header("requestingUserId", OTHER_OID))
            .andExpect(jsonPath("$[0].clientId").value("client-other"));
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        mockMvc.perform(get(PATH).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void name_under_eight_characters_should_be_rejected() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Reports\"}"))
            .andExpect(status().isBadRequest());

        stubRegistration();
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Report X\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void name_with_punctuation_should_be_rejected_before_entra() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"My App!! (v2)\"}"))
            .andExpect(status().isBadRequest());

        wireMockGraph().verify(0, postRequestedFor(urlPathEqualTo("/applications")));
    }

    private void signIn(final String oid, final String email) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }

    private void create(final String oid, final String name) throws Exception {
        stubRegistration();
        mockMvc.perform(post(PATH).header("requestingUserId", oid)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated());
    }
}
