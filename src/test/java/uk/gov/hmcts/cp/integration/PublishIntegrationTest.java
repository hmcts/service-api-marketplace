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

import com.jayway.jsonpath.JsonPath;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
            .andExpect(jsonPath("$.reference").isNotEmpty())
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
            .andExpect(jsonPath("$[0].reference").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void deleting_a_publish_request_should_return_204() throws Exception {
        String reference = submitAndReturnReference();

        mockMvc.perform(delete("/publish-requests/{reference}", reference))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_a_publish_request_by_the_reference_listed_in_requests_should_return_204() throws Exception {
        // As for subscriptions: the reference the requests list hands the web app is the
        // identifier delete has to accept, because it is the only one the web app is given.
        String reference = submitAndReturnReference();

        String listJson = mockMvc.perform(get("/requests").header("requestingUserId", 1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> listed = JsonPath.read(listJson, "$[?(@.reference=='" + reference + "')].reference");
        assertThat(listed).containsExactly(reference);

        mockMvc.perform(delete("/publish-requests/{reference}", listed.get(0)))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleting_an_unknown_publish_request_should_return_404() throws Exception {
        mockMvc.perform(delete("/publish-requests/{reference}", "PR-2026-ZZZZZZ"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleting_a_publish_request_by_a_uuid_should_return_404() throws Exception {
        // As for subscriptions: a caller holding an old primary key gets a 404 it can act
        // on, not the 400 a UUID-typed path variable used to give.
        mockMvc.perform(delete("/publish-requests/{reference}", UUID.randomUUID()))
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

    private String submitAndReturnReference() throws Exception {
        String responseJson = mockMvc.perform(post("/publish-requests")
                .header("requestingUserId", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(REQUEST)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return OBJECT_MAPPER.readTree(responseJson).get("reference").asText();
    }
}
