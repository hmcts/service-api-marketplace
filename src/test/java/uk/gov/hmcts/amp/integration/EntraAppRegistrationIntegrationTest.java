package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.API_CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.CLIENT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.OBJECT_ID;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.ROLE;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.assignedTo;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubApplicationDelete;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubApplicationList;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRoleAssignment;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubHeldRoles;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubRegistration;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.stubServicePrincipals;
import static uk.gov.hmcts.amp.integration.WireMockGraphInitialise.wireMockGraph;

/** The four endpoints against WireMock, so no credential and no network beyond localhost. */
@SpringBootTest(properties = {
    "entra.onboarding-client-id=a-test-onboarding-app",
    "entra.onboarding-client-secret=a-test-secret",
    "entra.api-client-id=" + API_CLIENT_ID,
    "entra.max-attempts=2",
    "entra.retry-delay-millis=1"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockGraphInitialise.class})
class EntraAppRegistrationIntegrationTest {

    private static final String PATH = "/applications-new";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void forgetPreviousStubs() {
        wireMockGraph().resetAll();
    }

    @Test
    void listing_applications_should_return_them_without_a_secret() throws Exception {
        stubApplicationList();

        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$[0].displayName").value("Team Alpha"))
            .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void registering_should_return_the_secret_and_the_roles_it_granted() throws Exception {
        stubRegistration();
        stubServicePrincipals();
        stubRoleAssignment();

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team Alpha\",\"roles\":[\"" + ROLE + "\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$.clientSecret").value("the-secret-shown-once"))
            .andExpect(jsonPath("$.roles[0]").value(ROLE));

        wireMockGraph().verify(postRequestedFor(urlPathEqualTo(assignedTo())));
    }

    @Test
    void registering_without_roles_should_still_create_the_application() throws Exception {
        stubRegistration();

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team Alpha\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID));

        // Roles are granted separately, so nothing is assigned here.
        wireMockGraph().verify(0, postRequestedFor(urlPathEqualTo(assignedTo())));
    }

    @Test
    void granting_a_role_should_answer_no_content() throws Exception {
        stubServicePrincipals();
        stubRoleAssignment();

        mockMvc.perform(post(PATH + "/" + CLIENT_ID + "/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + ROLE + "\"}"))
            .andExpect(status().isNoContent());

        wireMockGraph().verify(postRequestedFor(urlPathEqualTo(assignedTo())));
    }

    @Test
    void registering_without_a_name_should_be_rejected() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"roles\":[\"" + ROLE + "\"]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reading_one_application_should_name_the_roles_it_holds() throws Exception {
        stubApplicationList();
        stubServicePrincipals();
        stubHeldRoles();

        mockMvc.perform(get(PATH + "/" + CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$.roles[0]").value(ROLE));
    }

    @Test
    void deleting_an_application_should_remove_it_by_object_id() throws Exception {
        stubApplicationList();
        stubApplicationDelete();

        mockMvc.perform(delete(PATH + "/" + CLIENT_ID))
            .andExpect(status().isNoContent());

        wireMockGraph().verify(deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
    }

    @Test
    void deleting_an_application_that_is_not_there_should_be_not_found() throws Exception {
        stubApplicationList();

        mockMvc.perform(delete(PATH + "/does-not-exist"))
            .andExpect(status().isNotFound());
    }
}
