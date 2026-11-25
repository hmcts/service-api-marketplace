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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /admin/users/lookup over HTTP: the support view of who has signed in, for admins only. */
@SpringBootTest(properties = {
    "admin.auth.tenant-id=99999999-8888-7777-6666-555555555555",
    "admin.auth.audience=the-web-app"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class UserEndpointIntegrationTest {

    private static final String PATH = "/admin/users/lookup";
    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String GRACE = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    private long adaId;

    @BeforeEach
    void startClean() throws Exception {
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        adaId = signIn(ADA, "ada.lovelace@hmcts.net", "Ada Lovelace");
        signIn(GRACE, "grace.hopper@hmcts.net", "Grace Hopper");
        final UserEntity ada = users.findByEntraObjectId(ADA).orElseThrow();
        users.save(ada);
    }

    @Test
    void listing_should_show_everyone_who_has_signed_in_in_row_order() throws Exception {
        mockMvc.perform(adminGet(PATH).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("ada.lovelace@hmcts.net"))
            .andExpect(jsonPath("$[1].email").value("grace.hopper@hmcts.net"));
    }

    @Test
    void listing_should_not_expose_anything_a_sign_in_does_not() throws Exception {
        mockMvc.perform(adminGet(PATH).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].userReference").value("user%03d".formatted(adaId)))
            .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].role").value("ADMIN"))
            .andExpect(jsonPath("$[0].lastLoginAt").isNotEmpty());
    }

    @Test
    void row_id_should_find_the_user() throws Exception {
        mockMvc.perform(adminGet(PATH + "/" + adaId).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entraObjectId").value(ADA));
    }

    @Test
    void an_entra_object_id_should_find_the_same_user() throws Exception {
        mockMvc.perform(adminGet(PATH + "/" + ADA).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(adaId));
    }

    @Test
    void an_admin_should_be_able_to_read_somebody_else() throws Exception {
        mockMvc.perform(adminGet(PATH + "/" + GRACE).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("grace.hopper@hmcts.net"));
    }

    @Test
    void an_identifier_matching_nobody_should_be_not_found() throws Exception {
        mockMvc.perform(adminGet(PATH + "/999999").header("requestingUserId", ADA))
            .andExpect(status().isNotFound());

        mockMvc.perform(adminGet(PATH + "/not-an-identifier").header("requestingUserId", ADA))
            .andExpect(status().isNotFound());
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(adminGet(PATH)).andExpect(status().isBadRequest());
        mockMvc.perform(adminGet(PATH + "/" + adaId)).andExpect(status().isBadRequest());
    }

    @Test
    void listing_should_be_refused_for_someone_who_is_not_an_admin() throws Exception {
        mockMvc.perform(nonAdminGet(PATH).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void someone_who_is_not_an_admin_should_not_read_somebody_else() throws Exception {
        mockMvc.perform(nonAdminGet(PATH + "/" + ADA).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
        mockMvc.perform(nonAdminGet(PATH + "/" + adaId).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void someone_who_is_not_an_admin_should_not_read_themselves_here_either() throws Exception {
        mockMvc.perform(adminGet(PATH + "/" + GRACE).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        mockMvc.perform(adminGet(PATH).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    private long signIn(final String oid, final String email, final String name) throws Exception {
        String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static MockHttpServletRequestBuilder adminGet(final String uri) {
        return get(uri).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_amp_admin")));
    }


    /** A token from someone signed in who does not hold the role; the chain refuses it. */
    private static MockHttpServletRequestBuilder nonAdminGet(final String uri) {
        return get(uri).with(jwt());
    }

}
