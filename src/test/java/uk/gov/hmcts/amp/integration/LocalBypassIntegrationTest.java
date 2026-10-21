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
import uk.gov.hmcts.amp.services.EntraAppRegistrationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the bypass on and no Entra secret, no Graph and no APIM, the whole journey still works:
 * register, grant, subscribe, read the key, unsubscribe, delete.
 */
@SpringBootTest(properties = "local-bypass.enabled=true")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class LocalBypassIntegrationTest {

    private static final String OID = "cccccccc-1111-2222-3333-444444444444";
    private static final String KEYS = "/apim/subscription-keys";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntraAppRegistrationService registrations;

    @BeforeEach
    void startClean() throws Exception {
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + OID + "\",\"email\":\"ada.lovelace@hmcts.net\",\"name\":\"Ada\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void the_whole_journey_should_work_without_entra_or_apim() throws Exception {
        String created = mockMvc.perform(post("/applications").header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").isNotEmpty())
            .andExpect(jsonPath("$.clientSecret").isNotEmpty())
            .andExpect(jsonPath("$.tenantId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String clientId = JsonPath.read(created, "$.clientId");

        mockMvc.perform(post("/applications/" + clientId + "/roles").header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"app.read\"}"))
            .andExpect(status().isNoContent());

        String subscribed = mockMvc.perform(post(KEYS).header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId + "\",\"shortCode\":\"slc\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subscriptionKey").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String name = JsonPath.read(subscribed, "$.subscriptionName");
        String key = JsonPath.read(subscribed, "$.subscriptionKey");

        mockMvc.perform(get(KEYS).header("requestingUserId", OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value(name));
        mockMvc.perform(get(KEYS + "/" + name + "/values").header("requestingUserId", OID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryKey").value(key));

        mockMvc.perform(delete(KEYS + "/" + name).header("requestingUserId", OID))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/applications/" + clientId).header("requestingUserId", OID))
            .andExpect(status().isNoContent());
    }

    @Test
    void an_application_the_bypass_has_never_seen_should_still_be_deletable() throws Exception {
        String created = mockMvc.perform(post("/applications").header("requestingUserId", OID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Case Viewer\"}"))
            .andReturn().getResponse().getContentAsString();
        String clientId = JsonPath.read(created, "$.clientId");

        // As after a restart: the row survives in Postgres, the in-memory registration does not.
        registrations.delete(clientId);

        mockMvc.perform(delete("/applications/" + clientId).header("requestingUserId", OID))
            .andExpect(status().isNoContent());
    }
}
