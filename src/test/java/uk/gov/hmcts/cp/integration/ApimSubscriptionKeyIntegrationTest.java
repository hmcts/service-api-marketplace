package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

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
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.INSTANCE;
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.stubProductList;
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.stubSubscriptionAnswering;
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.stubSubscriptionLifecycle;
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.stubSubscriptionList;
import static uk.gov.hmcts.cp.integration.WireMockApimInitialise.wireMockApim;

/** The four endpoints against WireMock, so no credential and no network beyond localhost. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockApimInitialise.class})
class ApimSubscriptionKeyIntegrationTest {

    private static final String PRODUCT_ID = "example-product";
    private static final String PATH = "/apim/subscription-keys";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void forgetPreviousStubs() {
        wireMockApim().resetAll();
    }

    @Test
    void listing_keys_should_return_the_subscriptions_without_their_values() throws Exception {
        stubSubscriptionList();

        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").isNotEmpty())
            .andExpect(jsonPath("$[0].scope").isNotEmpty())
            // Lifted out of the scope path, so a listed key reads back the productId create took.
            .andExpect(jsonPath("$[0].productId").value("example-product"))
            .andExpect(jsonPath("$[0].primaryKey").doesNotExist());
    }

    @Test
    void creating_a_key_should_return_its_values_and_put_what_azure_expects() throws Exception {
        String name = "team-alpha";
        stubSubscriptionLifecycle(name);

        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"team-alpha","productId":"example-product","displayName":"Team Alpha"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.primaryKey").isNotEmpty())
            .andExpect(jsonPath("$.secondaryKey").isNotEmpty());

        // What the application put on the wire, not what the stand-in answered.
        wireMockApim().verify(putRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + name))
            .withRequestBody(equalToJson("""
                {"properties":{"scope":"/products/example-product",
                 "displayName":"Team Alpha","state":"active"}}""", true, true)));
    }

    @Test
    void creating_then_deleting_a_key_should_leave_it_unreadable() throws Exception {
        String name = "team-alpha";
        stubSubscriptionLifecycle(name);

        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"team-alpha","productId":"example-product","displayName":"Team Alpha"}"""))
            .andExpect(status().isCreated());

        mockMvc.perform(get(PATH + "/" + name + "/values"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryKey").isNotEmpty())
            .andExpect(jsonPath("$.scope").doesNotExist());

        mockMvc.perform(delete(PATH + "/" + name))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH + "/" + name + "/values"))
            .andExpect(status().isNotFound());

        wireMockApim().verify(deleteRequestedFor(urlPathEqualTo(INSTANCE + "/subscriptions/" + name))
            .withHeader("If-Match", equalTo("*")));
    }

    @Test
    void creating_a_key_with_a_name_azure_would_reject_should_return_400() throws Exception {
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Team Alpha!","productId":"example-product","displayName":"Team Alpha"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refusal_from_api_management_should_be_our_fault_not_a_bad_gateway() throws Exception {
        // 403 means API Management answered and said no: the service identity has no role on the
        // instance. That is our deployment at fault, not a broken upstream.
        stubSubscriptionAnswering("team-alpha", 403);

        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"team-alpha","productId":"example-product","displayName":"Team Alpha"}"""))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void api_management_failing_should_be_a_bad_gateway() throws Exception {
        stubSubscriptionAnswering("team-alpha", 500);

        mockMvc.perform(get(PATH + "/team-alpha/values"))
            .andExpect(status().isBadGateway());
    }

    @Test
    void not_found_should_name_the_instance_it_looked_in() throws Exception {
        stubSubscriptionAnswering("team-alpha", 404);

        mockMvc.perform(get(PATH + "/team-alpha/values"))
            .andExpect(status().isNotFound())
            // A missing instance returns 404 too, so the message has to say where it looked.
            .andExpect(jsonPath("$.error").value(
                "No subscription named team-alpha on sps-api-mgmt-sbox in rg-sps-platform-sbox"));
    }

    @Test
    void listing_products_should_return_the_ids_create_key_accepts() throws Exception {
        stubProductList();

        mockMvc.perform(get("/apim/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value("example-product"))
            .andExpect(jsonPath("$[0].state").value("published"))
            .andExpect(jsonPath("$[1].productId").value("cp-crime-schedulingandlisting"))
            // Not every product carries one, and an absent field is honest about that.
            .andExpect(jsonPath("$[1].description").doesNotExist());
    }

    @Test
    void creating_the_same_key_twice_should_be_refused_rather_than_silently_replacing_it() throws Exception {
        stubSubscriptionLifecycle("team-alpha");
        String body = """
            {"name":"team-alpha","productId":"example-product","displayName":"Team Alpha"}""";

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        // Azure's PUT would upsert and report it as created, returning the first key's values.
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void deleting_a_key_that_is_not_there_should_be_a_not_found() throws Exception {
        stubSubscriptionLifecycle("never-created");

        // Azure answers this with success, which would not tell you whether anything was revoked.
        mockMvc.perform(delete(PATH + "/never-created"))
            .andExpect(status().isNotFound());
    }
}
