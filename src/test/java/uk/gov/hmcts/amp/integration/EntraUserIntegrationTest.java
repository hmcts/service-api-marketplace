package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class EntraUserIntegrationTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startWithNoUsers() {
        jdbc.update("delete from application_api");
        jdbc.update("delete from application");
        jdbc.update("delete from marketplace_user");
    }

    @Test
    void first_sign_in_should_create_an_active_user() throws Exception {
        signIn(OID, "{\"email\":\"Joe@Example.com\",\"firstName\":\"Joe\",\"lastName\":\"Bloggs\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.entraObjectId").value(OID))
            .andExpect(jsonPath("$.email").value("joe@example.com"))
            .andExpect(jsonPath("$.firstName").value("Joe"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        assertThat(users()).isEqualTo(1);
    }

    @Test
    void signing_in_again_should_update_the_same_user_from_entra() throws Exception {
        final String first = signIn(OID, "{\"email\":\"joe@example.com\",\"firstName\":\"Joe\"}")
            .andReturn().getResponse().getContentAsString();

        signIn(OID.toUpperCase(), "{\"email\":\"joseph@example.com\",\"firstName\":\"Joseph\",\"lastName\":\"Bloggs\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idIn(first)))
            .andExpect(jsonPath("$.email").value("joseph@example.com"))
            .andExpect(jsonPath("$.firstName").value("Joseph"))
            .andExpect(jsonPath("$.lastName").value("Bloggs"));

        assertThat(users()).isEqualTo(1);
    }

    @Test
    void missing_names_should_be_stored_as_empty() throws Exception {
        signIn(OID, "{\"email\":\"joe@example.com\",\"firstName\":\"  \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value(""))
            .andExpect(jsonPath("$.lastName").value(""));
    }

    @Test
    void another_entra_user_bringing_a_taken_email_should_be_refused() throws Exception {
        signIn(OID, "{\"email\":\"joe@example.com\"}").andExpect(status().isOk());

        signIn(OTHER_OID, "{\"email\":\"JOE@example.com\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("This email address is already used by another account."));

        assertThat(users()).isEqualTo(1);
    }

    @Test
    void an_inactive_user_should_be_refused() throws Exception {
        signIn(OID, "{\"email\":\"joe@example.com\"}").andExpect(status().isOk());
        jdbc.update("update marketplace_user set status = 'SUSPENDED'");

        signIn(OID, "{\"email\":\"joe@example.com\"}").andExpect(status().isForbidden());
    }

    @Test
    void an_object_id_that_is_not_a_guid_should_be_refused() throws Exception {
        signIn("not-an-oid", "{\"email\":\"joe@example.com\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Not a valid Entra object id."));
    }

    @Test
    void missing_email_should_be_refused() throws Exception {
        signIn(OID, "{\"firstName\":\"Joe\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Enter a valid email address."));
    }

    @Test
    void the_same_api_should_not_be_subscribed_twice_and_deleting_an_application_removes_its_apis() throws Exception {
        final String body = signIn(OID, "{\"email\":\"joe@example.com\"}")
            .andReturn().getResponse().getContentAsString();
        final long appId = jdbc.queryForObject(
            "insert into application (user_id, name, appname, client_id, object_id, secret_hint, secret_key_id,"
                + " secret_expires_at, created_at) values (?, 'Case viewer', 'case-viewer', 'client-1',"
                + " 'object-1', 'Uc6', 'key-1', now(), now()) returning id",
            Long.class, idIn(body));
        final String insertApi = "insert into application_api"
            + " (application_id, short_code, product_id, subscription_name, key_hint, subscribed_at)"
            + " values (?, 'slc', 'cp-crime-schedulingandlisting-courtschedule', ?, 'abc', now())";
        jdbc.update(insertApi, appId, "amp-user001-case-viewer-slc");

        assertThat(rejected(() -> jdbc.update(insertApi, appId, "amp-user001-case-viewer-slc-2"))).isTrue();

        jdbc.update("delete from application where id = ?", appId);
        assertThat(jdbc.queryForObject("select count(*) from application_api", Integer.class)).isZero();
    }

    private ResultActions signIn(final String oid, final String body) throws Exception {
        return mockMvc.perform(put("/users/entra/" + oid).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int users() {
        return jdbc.queryForObject("select count(*) from marketplace_user", Integer.class);
    }

    private static long idIn(final String json) {
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private static boolean rejected(final Runnable insert) {
        try {
            insert.run();
            return false;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return true;
        }
    }
}
