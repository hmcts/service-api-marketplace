package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.cp.domain.EntraSignInRequest;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void user_ids_and_the_ids_pointing_at_them_should_be_bigint() {
        String type = "select data_type from information_schema.columns where table_name = ? and column_name = ?";

        assertThat(jdbcTemplate.queryForObject(type, String.class, "marketplace_user", "id")).isEqualTo("bigint");
        assertThat(jdbcTemplate.queryForObject(type, String.class, "application", "user_id")).isEqualTo("bigint");
    }

    @Test
    void each_sign_in_should_record_its_time_and_keep_the_creation_time() throws Exception {
        String oid = "aaaaaaaa-0000-0000-0000-000000000007";
        signIn(oid, "entra-times@example.com").andExpect(status().isOk());
        Map<String, Object> first = timesFor(oid);

        signIn(oid, "entra-times@example.com").andExpect(status().isOk());
        Map<String, Object> second = timesFor(oid);

        assertThat(first.get("created_at")).isNotNull();
        assertThat(second.get("created_at")).isEqualTo(first.get("created_at"));
        assertThat((Timestamp) second.get("last_signed_in_at"))
            .isAfterOrEqualTo((Timestamp) first.get("last_signed_in_at"));
    }

    private Map<String, Object> timesFor(final String oid) {
        return jdbcTemplate.queryForMap(
            "select created_at, last_signed_in_at from marketplace_user where entra_object_id = ?", oid);
    }
}
