package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
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

/** Reading and revoking keys against WireMock, so no credential and no network beyond localhost. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockApimInitialise.class})
class ApimSubscriptionKeyIntegrationTest {

    private static final String PATH = "/apim/subscription-keys";
    private static final String OWNER_OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String SOMEONE_ELSES = "amp-consumer-team-beta-slc";
    private static final String NEVER_CREATED = "amp-consumer-team-alpha-pcd";

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
        mine = "amp-consumer-team-alpha-slc";
        signIn(OWNER_OID);
        ApplicationEntity application = applications.save(teamAlpha());
        applicationApis.save(subscribed(application, "slc", mine));
        applicationApis.save(subscribed(application, "pcd", NEVER_CREATED));
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
    void refusal_from_api_management_should_be_our_fault_not_a_bad_gateway() throws Exception {
        // 403 means API Management answered and said no: the service identity has no role on the
        // instance. That is our deployment at fault, not a broken upstream.
        stubSubscriptionAnswering(mine, 403);

        mockMvc.perform(get(PATH + "/" + mine + "/values").header("requestingUserId", OWNER_OID))
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
    void deleting_a_key_that_is_not_there_should_be_a_not_found() throws Exception {
        stubSubscriptionLifecycle(NEVER_CREATED);

        // Azure answers this with success, which would not tell you whether anything was revoked.
        mockMvc.perform(delete(PATH + "/" + NEVER_CREATED).header("requestingUserId", OWNER_OID))
            .andExpect(status().isNotFound());
    }

    private void signIn(final String oid) throws Exception {
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"ada.lovelace@hmcts.net\","
                    + "\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
    }

    private ApplicationEntity teamAlpha() {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setUser(users.findByEntraObjectId(OWNER_OID).orElseThrow());
        entity.setName("Team Alpha");
        entity.setAppname("team-alpha");
        entity.setClientId("client-team-alpha");
        entity.setObjectId("object-team-alpha");
        entity.setSecretHint("Uc6");
        entity.setSecretKeyId("key-team-alpha");
        entity.setSecretExpiresAt(LocalDateTime.of(2028, 1, 1, 0, 0));
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }

    private static ApplicationApiEntity subscribed(final ApplicationEntity application, final String shortCode,
                                                   final String name) {
        ApplicationApiEntity entity = new ApplicationApiEntity();
        entity.setApplication(application);
        entity.setShortCode(shortCode);
        entity.setProductId("cp-crime-schedulingandlisting");
        entity.setSubscriptionName(name);
        entity.setKeyHint("a1b");
        entity.setSubscribedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }
}
