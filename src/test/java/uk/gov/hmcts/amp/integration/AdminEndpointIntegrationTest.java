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
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /admin over HTTP: what the Marketplace Team sees of everyone, and that nobody else sees it. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class AdminEndpointIntegrationTest {

    private static final String USERS = "/admin/users";
    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String GRACE = "bbbbbbbb-1111-2222-3333-444444444444";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductionRequestRepository productionRequests;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    private long adaId;
    private long graceId;

    @BeforeEach
    void startClean() throws Exception {
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        adaId = signIn(ADA, "ada.lovelace@hmcts.net", "Ada Lovelace");
        graceId = signIn(GRACE, "grace.hopper@hmcts.net", "Grace Hopper");
        final UserEntity ada = users.findById(adaId).orElseThrow();
        ada.setRole(UserRole.ADMIN);
        users.save(ada);
    }

    @Test
    void an_admin_should_see_every_active_user_newest_first() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$[0].name").value("Grace Hopper"))
            .andExpect(jsonPath("$[0].id").value(graceId))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$[0].lastLoginAt").isNotEmpty())
            .andExpect(jsonPath("$[1].email").value("ada.lovelace@hmcts.net"));
    }

    @Test
    void suspended_user_should_be_left_out_of_the_list() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        grace.setStatus("SUSPENDED");
        users.save(grace);

        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("ada.lovelace@hmcts.net"));
    }

    @Test
    void the_list_should_count_each_users_applications() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        applications.save(application(grace, "Case Viewer", "client-1"));
        applications.save(application(grace, "Listings", "client-2"));

        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].applicationCount").value(2))
            .andExpect(jsonPath("$[1].applicationCount").value(0));
    }

    @Test
    void the_list_should_not_expose_identifiers_or_secrets() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].entraObjectId").doesNotExist())
            .andExpect(jsonPath("$[0].userReference").doesNotExist())
            .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void missing_or_unknown_caller_should_be_refused_as_elsewhere() throws Exception {
        mockMvc.perform(get(USERS)).andExpect(status().isBadRequest());
        mockMvc.perform(get(USERS).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    private long signIn(final String oid, final String email, final String name) throws Exception {
        final String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static ApplicationEntity application(final UserEntity owner, final String name, final String clientId) {
        final ApplicationEntity application = new ApplicationEntity();
        application.setUser(owner);
        application.setName(name);
        application.setAppname(name.toLowerCase().replace(' ', '-'));
        application.setClientId(clientId);
        application.setObjectId("object-" + clientId);
        application.setSecretHint("Uc6");
        application.setSecretKeyId("key-" + clientId);
        application.setSecretExpiresAt(NOW.plusYears(2));
        application.setCreatedAt(NOW);
        return application;
    }
}