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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /users over HTTP, which is the only way to see who has signed in without database access. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class UserEndpointIntegrationTest {

    private static final String PATH = "/users";
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
    }

    @Test
    void listing_should_show_everyone_who_has_signed_in_in_row_order() throws Exception {
        mockMvc.perform(get(PATH).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("ada.lovelace@hmcts.net"))
            .andExpect(jsonPath("$[1].email").value("grace.hopper@hmcts.net"));
    }

    @Test
    void listing_should_not_expose_anything_a_sign_in_does_not() throws Exception {
        mockMvc.perform(get(PATH).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].userReference").value("user%03d".formatted(adaId)))
            .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].lastLoginAt").isNotEmpty());
    }

    @Test
    void row_id_should_find_the_user() throws Exception {
        mockMvc.perform(get(PATH + "/" + adaId).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entraObjectId").value(ADA));
    }

    @Test
    void an_entra_object_id_should_find_the_same_user() throws Exception {
        mockMvc.perform(get(PATH + "/" + ADA).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(adaId));
    }

    @Test
    void reading_somebody_else_should_be_allowed() throws Exception {
        mockMvc.perform(get(PATH + "/" + GRACE).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("grace.hopper@hmcts.net"));
    }

    @Test
    void an_identifier_matching_nobody_should_be_not_found() throws Exception {
        mockMvc.perform(get(PATH + "/999999").header("requestingUserId", ADA))
            .andExpect(status().isNotFound());

        mockMvc.perform(get(PATH + "/not-an-identifier").header("requestingUserId", ADA))
            .andExpect(status().isNotFound());
    }

    @Test
    void missing_header_should_be_rejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/" + adaId)).andExpect(status().isBadRequest());
    }

    @Test
    void an_unknown_requesting_user_should_be_told_to_sign_in_again() throws Exception {
        mockMvc.perform(get(PATH).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    private long signIn(final String oid, final String email, final String name) throws Exception {
        String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
