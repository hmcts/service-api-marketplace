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
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.INSTANCE;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.stubSubscriptionLifecycle;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.wireMockApim;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.API_CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistration;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.wireMockGraph;

/** Subscribing an application to one API, which is where application_api is written and removed. */
@SpringBootTest(properties = {
    "entra.onboarding-client-id=a-test-onboarding-app",
    "entra.onboarding-client-secret=a-test-secret",
    "entra.api-client-id=" + API_CLIENT_ID,
    "entra.max-attempts=2",
    "entra.retry-delay-millis=1",
    "apim.resource-group=" + WireMockApimInitialise.RESOURCE_GROUP,
    "apim.service-name=" + WireMockApimInitialise.SERVICE_NAME})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockGraphInitialise.class,
    WireMockApimInitialise.class})
class ApplicationApiIntegrationTest {

    private static final String PATH = "/apim/subscription-keys";
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

    private String subscriptionName;

    @BeforeEach
    void startClean() throws Exception {
        wireMockGraph().resetAll();
        wireMockApim().resetAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        signIn(OWNER_OID, "ada.lovelace@hmcts.net");
        signIn(OTHER_OID, "grace.hopper@hmcts.net");
        createApplication();
        subscriptionName = "amp-user%03d-case-viewer-slc".formatted(
            users.findByEntraObjectId(OWNER_OID).orElseThrow().getId());
        stubSubscriptionLifecycle(subscriptionName);
    }

    @Test
    void subscribing_should_name_the_subscription_from_the_user_appname_and_short_code() throws Exception {
        mockMvc.perform(subscribeTo("slc"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("slc"))
            .andExpect(jsonPath("$.title").value("Scheduling and Listing Court Schedule"))
            .andExpect(jsonPath("$.productId").value("cp-crime-schedulingandlisting"))
            .andExpect(jsonPath("$.subscriptionName").value(subscriptionName))
            .andExpect(jsonPath("$.subscriptionKey").isNotEmpty());

        wireMockApim().verify(putRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + subscriptionName))
            .withRequestBody(equalToJson("""
                {"properties":{"scope":"/products/cp-crime-schedulingandlisting"}}""", true, true)));
    }

    @Test
    void subscribing_should_record_the_api_against_the_application() throws Exception {
        mockMvc.perform(subscribeTo("slc")).andExpect(status().isCreated());

        assertThat(applicationApis.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getShortCode()).isEqualTo("slc");
            assertThat(row.getProductId()).isEqualTo("cp-crime-schedulingandlisting");
            assertThat(row.getSubscriptionName()).isEqualTo(subscriptionName);
            assertThat(row.getKeyHint()).hasSize(3);
            assertThat(row.getSubscribedAt()).isNotNull();
        });
    }

    @Test
    void the_key_hint_should_be_stored_rather_than_the_key() throws Exception {
        String body = mockMvc.perform(subscribeTo("slc")).andReturn().getResponse().getContentAsString();

        ApplicationApiEntity row = applicationApis.findAll().getFirst();
        assertThat(body).contains(row.getKeyHint());
        assertThat(row.getKeyHint()).hasSize(3);
    }

    @Test
    void an_unknown_short_code_should_be_rejected_before_apim() throws Exception {
        mockMvc.perform(subscribeTo("nope")).andExpect(status().isBadRequest());

        assertThat(applicationApis.count()).isZero();
        wireMockApim().verify(0, putRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + subscriptionName)));
    }

    @Test
    void subscribing_twice_to_the_same_api_should_conflict() throws Exception {
        mockMvc.perform(subscribeTo("slc")).andExpect(status().isCreated());

        mockMvc.perform(subscribeTo("slc")).andExpect(status().isConflict());

        assertThat(applicationApis.count()).isEqualTo(1);
    }

    @Test
    void subscribing_someone_elses_application_should_be_not_found() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OTHER_OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"shortCode\":\"slc\"}"))
            .andExpect(status().isNotFound());

        assertThat(applicationApis.count()).isZero();
    }

    @Test
    void deleting_the_subscription_should_take_the_row_with_it() throws Exception {
        mockMvc.perform(subscribeTo("slc")).andExpect(status().isCreated());

        mockMvc.perform(get(PATH + "/" + subscriptionName + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryKey").isNotEmpty());

        mockMvc.perform(delete(PATH + "/" + subscriptionName).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH + "/" + subscriptionName + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());
        assertThat(applicationApis.count()).isZero();
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"shortCode\":\"slc\"}"))
            .andExpect(status().isBadRequest());
    }

    private RequestBuilder subscribeTo(final String shortCode) {
        return post(PATH).header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientId\":\"" + CLIENT_ID + "\",\"shortCode\":\"" + shortCode + "\"}");
    }

    private void signIn(final String oid, final String email) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }

    private void createApplication() throws Exception {
        stubRegistration();
        mockMvc.perform(post("/applications").header("requestingUserId", OWNER_OID)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isCreated());
    }
}
