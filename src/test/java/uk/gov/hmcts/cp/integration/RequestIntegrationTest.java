package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class RequestIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static final SubscriptionRequest SUBSCRIPTION = SubscriptionRequest.builder()
        .apiShortCode("pcd")
        .api("Test API")
        .environment("SBOX")
        .expectedVolume("LOW")
        .useCase("Integration test use case")
        .oauth2Capable(true)
        .declaration("I agree")
        .build();

    private static final PublishRequest PUBLICATION = PublishRequest.builder()
        .apiName("Court Schedule")
        .owningTeam("Scheduling and Listing")
        .contactEmail("sandl-api@justice.gov.uk")
        .specUrl("https://raw.githubusercontent.com/hmcts/api-cp-crime-slc/main/openapi-spec.yml")
        .build();

    private void submitOneOfEach() throws Exception {
        mockMvc.perform(post("/subscriptions")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SUBSCRIPTION)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(PUBLICATION)))
            .andExpect(status().isCreated());
    }

    @Test
    void listing_requests_should_return_both_kinds_in_one_list() throws Exception {
        submitOneOfEach();

        mockMvc.perform(get("/requests").header("requestingUserId", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.type == 'SUBSCRIPTION')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.type == 'PUBLISH')]").isNotEmpty());
    }

    @Test
    void every_row_should_carry_a_reference_a_type_a_date_and_a_status() throws Exception {
        submitOneOfEach();

        mockMvc.perform(get("/requests").header("requestingUserId", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reference").isNotEmpty())
            .andExpect(jsonPath("$[0].type").isNotEmpty())
            .andExpect(jsonPath("$[0].submittedAt").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void each_row_should_carry_nothing_the_list_does_not_need() throws Exception {
        submitOneOfEach();

        mockMvc.perform(get("/requests").header("requestingUserId", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].useCase").doesNotExist())
            .andExpect(jsonPath("$[0].specUrl").doesNotExist())
            .andExpect(jsonPath("$[0].requestingUserEmail").doesNotExist());
    }

    @Test
    void listing_with_an_unknown_user_id_should_return_401() throws Exception {
        mockMvc.perform(get("/requests").header("requestingUserId", 999))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listing_without_a_user_id_should_return_400() throws Exception {
        mockMvc.perform(get("/requests"))
            .andExpect(status().isBadRequest());
    }
}
