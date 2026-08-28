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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.orgName").value("Api Marketplace"))
            .andExpect(jsonPath("$.apiShortCode").value("pcd"));
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
