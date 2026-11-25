package uk.gov.hmcts.amp.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin gate, in an environment that has been configured for it. The issuer is never reached:
 * these tests hand the chain a token it has already decoded, so what is under test is who the
 * chain lets through rather than Nimbus.
 */
@SpringBootTest(properties = {
    "admin.auth.tenant-id=99999999-8888-7777-6666-555555555555",
    "admin.auth.audience=the-web-app"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class AdminSecurityIntegrationTest {

    private static final String USERS = "/admin/users";
    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";

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

    @BeforeEach
    void startClean() throws Exception {
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        final long adaId = signIn(ADA, "ada.lovelace@hmcts.net", "Ada Lovelace");
        final UserEntity ada = users.findById(adaId).orElseThrow();
        ada.setRole(UserRole.ADMIN);
        users.save(ada);
    }

    @Test
    void an_admin_should_be_served_the_users_behind_the_gate() throws Exception {
        mockMvc.perform(get(USERS).with(adminToken()).header("requestingUserId", ADA))
            .andExpect(status().isOk());
    }

    @Test
    void someone_signed_in_without_the_role_should_be_refused() throws Exception {
        mockMvc.perform(get(USERS).with(jwt().jwt(token -> token.claim("oid", ADA)))
                .header("requestingUserId", ADA))
            .andExpect(status().isForbidden());
    }

    @Test
    void no_token_at_all_should_be_refused_rather_than_served() throws Exception {
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
    }

    @Test
    void the_header_the_service_has_always_read_should_not_open_an_admin_path() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(status().isUnauthorized());
    }

    /** The point of the second chain: everything a consumer calls is untouched by any of this. */
    @Test
    void consumer_paths_should_still_be_served_without_a_token() throws Exception {
        mockMvc.perform(get("/catalogue")).andExpect(status().isOk());
    }

    @Test
    void the_health_endpoint_should_still_answer_without_a_token() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    private static RequestPostProcessor adminToken() {
        return jwt()
            .jwt(token -> token.claim("oid", ADA).claim("roles", List.of("amp_admin")))
            .authorities(new SimpleGrantedAuthority("ROLE_amp_admin"));
    }

    private long signIn(final String oid, final String email, final String name) throws Exception {
        final String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
