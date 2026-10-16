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
import uk.gov.hmcts.amp.repository.UserRepository;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.INSTANCE;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.stubSubscriptionAnswering;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.stubSubscriptionLifecycle;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.stubSubscriptionList;
import static uk.gov.hmcts.amp.integration.WireMockApimInitialise.wireMockApim;

/** The four endpoints against WireMock, so no credential and no network beyond localhost. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockApimInitialise.class})
class ApimSubscriptionKeyIntegrationTest {

    private static final String PATH = "/apim/subscription-keys";
    private static final String OWNER_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String SOMEONE_ELSES = "amp-user999-team-beta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    private String mine;

    @BeforeEach
    void startClean() throws Exception {
        wireMockApim().resetAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        mine = prefixOf(OWNER_OID) + "team-alpha";
    }

    @Test
    void listing_keys_should_return_the_subscriptions_without_their_values() throws Exception {
        stubSubscriptionList(mine);

        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value(mine))
            .andExpect(jsonPath("$[0].scope").isNotEmpty())
            // Lifted out of the scope path, so a listed key reads back the productId create took.
            .andExpect(jsonPath("$[0].productId").value("example-product"))
            .andExpect(jsonPath("$[0].primaryKey").doesNotExist());
    }

    @Test
    void listing_keys_should_leave_out_the_ones_belonging_to_someone_else() throws Exception {
        stubSubscriptionList(mine, SOMEONE_ELSES);

        mockMvc.perform(get(PATH).header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value(mine));
    }

    @Test
    void creating_a_key_should_return_its_values_and_put_what_azure_expects() throws Exception {
        stubSubscriptionLifecycle(mine);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(mine)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.primaryKey").isNotEmpty())
            .andExpect(jsonPath("$.secondaryKey").isNotEmpty());

        // What the application put on the wire, not what the stand-in answered.
        wireMockApim().verify(putRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + mine))
            .withRequestBody(equalToJson("""
                {"properties":{"scope":"/products/example-product",
                 "displayName":"Team Alpha","state":"active"}}""", true, true)));
    }

    @Test
    void creating_then_deleting_a_key_should_leave_it_unreadable() throws Exception {
        stubSubscriptionLifecycle(mine);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(mine)))
            .andExpect(status().isCreated());

        mockMvc.perform(get(PATH + "/" + mine + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryKey").isNotEmpty())
            .andExpect(jsonPath("$.scope").doesNotExist());

        mockMvc.perform(delete(PATH + "/" + mine).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH + "/" + mine + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());

        wireMockApim().verify(deleteRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + mine))
            .withHeader("If-Match", equalTo("*")));
    }

    @Test
    void creating_a_name_outside_your_own_should_be_refused_before_azure() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(SOMEONE_ELSES)))
            .andExpect(status().isBadRequest());

        wireMockApim().verify(0, putRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + SOMEONE_ELSES)));
    }

    @Test
    void reading_someone_elses_key_values_should_be_not_found() throws Exception {
        stubSubscriptionLifecycle(SOMEONE_ELSES);

        mockMvc.perform(get(PATH + "/" + SOMEONE_ELSES + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleting_someone_elses_subscription_should_be_not_found_and_leave_it_alone() throws Exception {
        stubSubscriptionLifecycle(SOMEONE_ELSES);

        mockMvc.perform(delete(PATH + "/" + SOMEONE_ELSES).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());

        wireMockApim().verify(0, deleteRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + SOMEONE_ELSES)));
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
    void creating_a_key_with_a_name_azure_would_reject_should_return_400() throws Exception {
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("Team Alpha!")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refusal_from_api_management_should_be_our_fault_not_a_bad_gateway() throws Exception {
        // 403 means API Management answered and said no: the service identity has no role on the
        // instance. That is our deployment at fault, not a broken upstream.
        stubSubscriptionAnswering(mine, 403);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(mine)))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void api_management_failing_should_be_a_bad_gateway() throws Exception {
        stubSubscriptionAnswering(mine, 500);

        mockMvc.perform(get(PATH + "/" + mine + "/values").header("requestingUserId", OWNER_OID))
            .andExpect(status().isBadGateway());
    }

    @Test
    void not_found_should_name_the_instance_it_looked_in() throws Exception {
        stubSubscriptionAnswering(mine, 404);

        mockMvc.perform(delete(PATH + "/" + mine).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound())
            // A missing instance returns 404 too, so the message has to say where it looked.
            .andExpect(jsonPath("$.error").value(
                "No subscription named " + mine + " on sps-api-mgmt-sbox in rg-sps-platform-sbox"));
    }

    @Test
    void creating_the_same_key_twice_should_be_refused_rather_than_silently_replacing_it() throws Exception {
        stubSubscriptionLifecycle(mine);

        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(mine)))
            .andExpect(status().isCreated());

        // Azure's PUT would upsert and report it as created, returning the first key's values.
        mockMvc.perform(post(PATH).header("requestingUserId", OWNER_OID)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(mine)))
            .andExpect(status().isConflict());
    }

    @Test
    void deleting_a_key_that_is_not_there_should_be_a_not_found() throws Exception {
        String neverCreated = prefixOf(OWNER_OID) + "never-created";
        stubSubscriptionLifecycle(neverCreated);

        // Azure answers this with success, which would not tell you whether anything was revoked.
        mockMvc.perform(delete(PATH + "/" + neverCreated).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());
    }

    private static String createBody(final String name) {
        return """
            {"name":"%s","productId":"example-product","displayName":"Team Alpha"}""".formatted(name);
    }

    /** The reference is the row id, so it has to come back from the sign in rather than be assumed. */
    private String prefixOf(final String oid) throws Exception {
        String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"ada.lovelace@hmcts.net\","
                    + "\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return "amp-" + JsonPath.<String>read(body, "$.userReference") + "-";
    }
}
