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

import com.jayway.jsonpath.JsonPath;
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
            .andExpect(jsonPath("$.id").isNotEmpty())
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
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("NEW"))
            .andExpect(jsonPath("$[0].requestingOrgName").value("Api Marketplace"));
    }

    @Test
    void deleting_a_subscription_should_return_204() throws Exception {
        String responseJson = mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(responseJson, "$.id");

        mockMvc.perform(delete("/subscriptions/{id}", id))
            .andDo(print())
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_an_unknown_subscription_should_return_404() throws Exception {
        mockMvc.perform(delete("/subscriptions/{id}", java.util.UUID.randomUUID()))
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
}
