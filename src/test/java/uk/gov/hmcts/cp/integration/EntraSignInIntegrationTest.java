package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.cp.domain.EntraSignInRequest;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class EntraSignInIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private ResultActions signIn(final String oid, final String email) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(
            EntraSignInRequest.builder().email(email).firstName("Jo").lastName("Bloggs").build());
        return mockMvc.perform(put("/users/entra/" + oid).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void first_sign_in_should_create_a_consumer_in_the_default_organisation() throws Exception {
        signIn("aaaaaaaa-0000-0000-0000-000000000001", "entra-new@example.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("entra-new@example.com"))
            .andExpect(jsonPath("$.firstName").value("Jo"))
            .andExpect(jsonPath("$.orgName").value("Not specified"))
            .andExpect(jsonPath("$.role").value("consumer"));
    }

    @Test
    void signing_in_again_with_a_new_email_should_keep_the_same_account() throws Exception {
        String oid = "aaaaaaaa-0000-0000-0000-000000000002";
        String first = signIn(oid, "entra-before@example.com").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        int id = OBJECT_MAPPER.readTree(first).get("id").asInt();

        signIn(oid, "entra-after@example.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(id)))
            .andExpect(jsonPath("$.email").value("entra-after@example.com"));
    }

    @Test
    void seeded_account_should_be_linked_and_keep_its_name_and_organisation() throws Exception {
        signIn("aaaaaaaa-0000-0000-0000-000000000003", "Zaheer.Iqbal@hmcts.net")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("zaheer.iqbal@hmcts.net"))
            .andExpect(jsonPath("$.firstName").value("Zaheer"))
            .andExpect(jsonPath("$.orgName").value("Api Marketplace"));
    }

    @Test
    void an_account_linked_to_another_entra_user_should_be_refused_with_409() throws Exception {
        signIn("aaaaaaaa-0000-0000-0000-000000000004", "entra-owned@example.com").andExpect(status().isOk());

        signIn("aaaaaaaa-0000-0000-0000-000000000005", "entra-owned@example.com")
            .andExpect(status().isConflict());
    }

    @Test
    void malformed_object_id_should_be_refused_with_400() throws Exception {
        signIn("not-a-guid", "entra-bad-oid@example.com").andExpect(status().isBadRequest());
    }

    @Test
    void an_invalid_email_should_be_refused_with_400() throws Exception {
        signIn("aaaaaaaa-0000-0000-0000-000000000006", "not-an-email").andExpect(status().isBadRequest());
    }
}
