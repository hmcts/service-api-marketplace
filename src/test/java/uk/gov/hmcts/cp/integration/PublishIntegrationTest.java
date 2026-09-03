package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import uk.gov.hmcts.cp.domain.PublishRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class PublishIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static final PublishRequest REQUEST = PublishRequest.builder()
        .apiName("Court Schedule")
        .owningTeam("Scheduling and Listing")
        .contactEmail("sandl-api@justice.gov.uk")
        .specUrl("https://raw.githubusercontent.com/hmcts/api-cp-crime-slc/main/openapi-spec.yml")
        .build();

    @Test
    void submitting_a_valid_publish_request_should_return_201_with_response() throws Exception {
        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.requestingOrgName").value("Api Marketplace"))
            .andExpect(jsonPath("$.apiName").value("Court Schedule"))
            .andExpect(jsonPath("$.owningTeam").value("Scheduling and Listing"));
    }

    @Test
    void the_requester_should_be_stamped_from_the_user_and_not_the_body() throws Exception {
        // Nothing in the body names the requester, so the response can only be showing
        // details the backend looked up from the user id in the header.
        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requestingUserName").isNotEmpty())
            .andExpect(jsonPath("$.requestingUserEmail").isNotEmpty());
    }

    @Test
    void getting_all_publish_requests_should_return_200_with_list() throws Exception {
        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/publish-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void deleting_a_publish_request_should_return_204() throws Exception {
        String responseJson = mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String id = OBJECT_MAPPER.readTree(responseJson).get("id").asText();

        mockMvc.perform(delete("/publish-requests/" + id))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_an_unknown_publish_request_should_return_404() throws Exception {
        mockMvc.perform(delete("/publish-requests/00000000-0000-4000-8000-000000000000"))
            .andExpect(status().isNotFound());
    }

    @Test
    void submitting_with_missing_fields_should_return_400() throws Exception {
        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submitting_with_an_unknown_user_id_should_return_401() throws Exception {
        mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 999)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isUnauthorized());
    }
}
