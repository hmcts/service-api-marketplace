package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class SubscriptionIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static final SubscriptionRequest REQUEST = SubscriptionRequest.builder()
        .apiShortCode("pcd")
        .api("Test API")
        .environment("SBOX")
        .expectedVolume("LOW")
        .useCase("Integration test use case")
        .oauth2Capable(true)
        .declaration("I agree")
        .build();

    @Test
    void submitting_a_valid_subscription_request_should_return_201_with_response() throws Exception {
        mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").isNotEmpty())
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.requestingOrgName").value("Api Marketplace"))
            .andExpect(jsonPath("$.apiShortCode").value("pcd"));
    }

    @Test
    void getting_all_subscriptions_should_return_200_with_list() throws Exception {
        mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/subscriptions"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].reference").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("NEW"))
            .andExpect(jsonPath("$[0].requestingOrgName").value("Api Marketplace"));
    }

    @Test
    void deleting_a_subscription_should_return_204() throws Exception {
        String reference = submitAndReturnReference();

        mockMvc.perform(delete("/subscriptions/{reference}", reference))
            .andDo(print())
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_a_subscription_by_the_reference_listed_in_requests_should_return_204() throws Exception {
        // The requests list is the only place the web app learns of an existing request, and
        // since AMP-1071 it carries the reference and not the primary key. Deleting with the
        // value the list actually returned is what a user clicking "withdraw" does, so the
        // delete route has to accept it - taking the id from the POST response instead would
        // exercise an identifier the web app can no longer obtain.
        String reference = submitAndReturnReference();

        String listJson = mockMvc.perform(get("/requests").header("requestingUserId", 1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> listed = JsonPath.read(listJson, "$[?(@.reference=='" + reference + "')].reference");
        assertThat(listed).containsExactly(reference);

        mockMvc.perform(delete("/subscriptions/{reference}", listed.get(0)))
            .andDo(print())
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_an_unknown_subscription_should_return_404() throws Exception {
        mockMvc.perform(delete("/subscriptions/{reference}", "AR-2026-ZZZZZZ"))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void deleting_a_subscription_by_a_uuid_should_return_404() throws Exception {
        // The primary key is no longer exposed at all, but a caller holding one from before
        // gets a "no such request" it can act on rather than the 400 a UUID-typed path
        // variable used to give.
        mockMvc.perform(delete("/subscriptions/{reference}", UUID.randomUUID()))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void submitting_with_unknown_user_id_should_return_401() throws Exception {
        mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 999)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isUnauthorized());
    }

    private String submitAndReturnReference() throws Exception {
        String responseJson = mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(responseJson, "$.reference");
    }
}
