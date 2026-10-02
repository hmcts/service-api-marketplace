package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account and application endpoints against a real Postgres, so what unit tests cannot see is
 * checked: that Flyway's migrations apply, that the entities map the columns, that the "owned or
 * invited" query and the unique indexes behave, and that cascades fire.
 *
 * <p>The database is shared with the other integration tests, so every test makes its own users,
 * and its own client address: sign-in and registration are limited to 20 attempts per address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
@TestPropertySource(properties = "JWT_SECRET=integration-test-signing-secret-0123456789")
class AccountAndApplicationIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "an-integration-test-password";
    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    // Each test calls this once, so it has its own address and its own rate-limit allowance.
    private RequestPostProcessor newClient() {
        String address = "10.20." + (NEXT_CLIENT.get() / 250) + "." + (NEXT_CLIENT.getAndIncrement() % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String unique(final String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode read(final MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(final MockHttpServletRequestBuilder builder, final String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String register(final RequestPostProcessor client, final String email, final String organisation)
        throws Exception {
        MvcResult result = mvc.perform(json(post("/api/register").with(client),
                "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"" + email + "\",\"organisation\":\""
                    + organisation + "\",\"role\":\"consumer\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return read(result).get("token").asText();
    }

    private JsonNode createApplication(final String token, final String name) throws Exception {
        return read(mvc.perform(json(post("/api/applications").header("Authorization", "Bearer " + token),
                "{\"name\":\"" + name + "\",\"environment\":\"sandbox\"}"))
            .andExpect(status().isCreated())
            .andReturn());
    }

    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    // ---------------------------------------------------------------------------------- accounts

    @Test
    void registering_then_signing_in_then_asking_who_i_am_should_all_agree() throws Exception {
        RequestPostProcessor client = newClient();
        String email = unique("account") + "@example.com";
        // A brand-new organisation: the first one created through the app used to collide with the
        // seeded organisation's id, because V1.001 inserted it with an explicit id.
        String registered = register(client, email, unique("New Organisation"));

        MvcResult login = mvc.perform(json(post("/api/login").with(client),
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.role").value("consumer"))
            .andReturn();

        mvc.perform(get("/api/me").header("Authorization", bearer(read(login).get("token").asText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email));
        mvc.perform(get("/api/me").header("Authorization", bearer(registered)))
            .andExpect(status().isOk());
    }

    @Test
    void two_people_naming_the_same_organisation_in_different_cases_should_share_it() throws Exception {
        RequestPostProcessor client = newClient();
        String organisation = unique("Shared Org");

        register(client, unique("first") + "@example.com", organisation);
        register(client, unique("second") + "@example.com", organisation.toUpperCase());
    }

    @Test
    void wrong_password_an_unknown_email_and_a_seeded_account_should_all_be_refused_alike() throws Exception {
        RequestPostProcessor client = newClient();
        String email = unique("refused") + "@example.com";
        register(client, email, "Org");

        for (String body : new String[] {
            "{\"email\":\"" + email + "\",\"password\":\"the-wrong-password\"}",
            "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"" + PASSWORD + "\"}",
            // Seeded accounts carry a placeholder hash, so none of them can be signed in to.
            "{\"email\":\"colin.greenwood@hmcts.net\",\"password\":\"CHANGE_ME\"}"}) {
            mvc.perform(json(post("/api/login").with(client), body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Incorrect email or password."));
        }
    }

    @Test
    void registering_an_email_twice_should_be_refused_without_saying_it_is_taken() throws Exception {
        RequestPostProcessor client = newClient();
        String email = unique("twice") + "@example.com";
        register(client, email, "Org");

        mvc.perform(json(post("/api/register").with(client),
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email.toUpperCase()
                    + "\",\"role\":\"consumer\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("An account with these details could not be created."));
    }

    @Test
    void token_that_is_not_ours_should_not_identify_anyone() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer not.a.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Session expired. Please sign in again."));
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Not signed in."));
    }

    // ------------------------------------------------------------------------------ applications

    @Test
    void creating_an_application_should_show_it_to_its_owner_and_hide_it_from_everyone_else() throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("owner") + "@example.com", "Org");
        String stranger = register(client, unique("stranger") + "@example.com", "Org");
        String name = unique("Alpha");

        JsonNode created = createApplication(owner, name);
        String id = created.get("application").get("id").asText();
        String secret = created.get("apiKey").asText();
        assertThat(secret).matches("amp_[0-9a-f]{48}");
        assertThat(created.get("application").get("clientId").asText()).isEqualTo(id);

        mvc.perform(get("/api/applications").header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applications[?(@.id=='" + id + "')].viewerRole").value("owner"));
        mvc.perform(get("/api/applications").header("Authorization", bearer(stranger)))
            .andExpect(jsonPath("$.applications[?(@.id=='" + id + "')]").isEmpty());

        MvcResult detail = mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiKeys.length()").value(1))
            .andExpect(jsonPath("$.apiKeys[0].preview").value(secret.substring(secret.length() - 4)))
            .andReturn();
        assertThat(detail.getResponse().getContentAsString()).doesNotContain(secret);
        mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(stranger)))
            .andExpect(status().isNotFound());
    }

    @Test
    void the_same_name_should_be_refused_twice_in_one_environment_and_production_should_not_be_open_yet()
        throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("dup") + "@example.com", "Org");
        String name = unique("Dup");
        createApplication(owner, name);

        mvc.perform(json(post("/api/applications").header("Authorization", bearer(owner)),
                "{\"name\":\"" + name.toUpperCase() + "\",\"environment\":\"sandbox\"}"))
            .andExpect(status().isConflict());
        mvc.perform(json(post("/api/applications").header("Authorization", bearer(owner)),
                "{\"name\":\"Other\",\"environment\":\"production\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The production environment is not available yet."));
    }

    @Test
    void an_invited_person_should_see_the_application_with_the_role_they_were_given() throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("owner") + "@example.com", "Org");
        String danEmail = unique("dan") + "@example.com";
        String dan = register(client, danEmail, "Org");
        String id = createApplication(owner, unique("Team")).get("application").get("id").asText();

        mvc.perform(json(post("/api/applications/" + id + "/team-members")
                .header("Authorization", bearer(owner)),
                "{\"email\":\"" + danEmail.toUpperCase() + "\",\"role\":\"developer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.teamMember.email").value(danEmail));

        mvc.perform(get("/api/applications").header("Authorization", bearer(dan)))
            .andExpect(jsonPath("$.applications[?(@.id=='" + id + "')].viewerRole").value("developer"));
        mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(dan)))
            .andExpect(status().isOk());
    }

    @Test
    void developer_can_connect_apis_but_not_change_details_and_an_administrator_can() throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("owner") + "@example.com", "Org");
        String devEmail = unique("dev") + "@example.com";
        String adminEmail = unique("admin") + "@example.com";
        String dev = register(client, devEmail, "Org");
        String admin = register(client, adminEmail, "Org");
        String id = createApplication(owner, unique("Roles")).get("application").get("id").asText();
        String team = "/api/applications/" + id + "/team-members";
        mvc.perform(json(post(team).header("Authorization", bearer(owner)),
            "{\"email\":\"" + devEmail + "\",\"role\":\"developer\"}")).andExpect(status().isCreated());
        mvc.perform(json(post(team).header("Authorization", bearer(owner)),
            "{\"email\":\"" + adminEmail + "\",\"role\":\"administrator\"}")).andExpect(status().isCreated());

        mvc.perform(json(post("/api/applications/" + id + "/connected-apis").header("Authorization", bearer(dev)),
                "{\"id\":\"api-1\",\"name\":\"API One\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.application.connectedApis[0].id").value("api-1"));
        mvc.perform(json(patch("/api/applications/" + id).header("Authorization", bearer(dev)),
                "{\"description\":\"hijack\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/applications/" + id).header("Authorization", bearer(admin)),
                "{\"description\":\"by admin\",\"customAttributes\":{\"tier\":\"gold\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.description").value("by admin"))
            .andExpect(jsonPath("$.application.customAttributes.tier").value("gold"))
            .andExpect(jsonPath("$.application.connectedApis[0].id").value("api-1"));
        mvc.perform(json(put("/api/applications/" + id).header("Authorization", bearer(admin)),
                "{\"description\":\"by put\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.description").value("by put"));
        mvc.perform(delete("/api/applications/" + id).header("Authorization", bearer(admin)))
            .andExpect(status().isForbidden());
    }

    @Test
    void secrets_can_be_made_and_revoked_and_the_list_shows_which_are_still_live() throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("owner") + "@example.com", "Org");
        JsonNode created = createApplication(owner, unique("Secrets"));
        String id = created.get("application").get("id").asText();
        String url = "/api/applications/" + id + "/api-keys";

        JsonNode second = read(mvc.perform(post(url).header("Authorization", bearer(owner)))
            .andExpect(status().isCreated()).andReturn());
        mvc.perform(delete(url + "/" + second.get("id").asText()).header("Authorization", bearer(owner)))
            .andExpect(status().isOk());

        mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(owner)))
            .andExpect(jsonPath("$.apiKeys.length()").value(2))
            .andExpect(jsonPath("$.apiKeys[?(@.id=='" + second.get("id").asText() + "')].revokedAt").isNotEmpty());
    }

    @Test
    void removing_a_team_member_should_end_their_access_at_once_and_deleting_should_remove_everything()
        throws Exception {
        RequestPostProcessor client = newClient();
        String owner = register(client, unique("owner") + "@example.com", "Org");
        String danEmail = unique("dan") + "@example.com";
        String dan = register(client, danEmail, "Org");
        String id = createApplication(owner, unique("Gone")).get("application").get("id").asText();
        JsonNode member = read(mvc.perform(json(post("/api/applications/" + id + "/team-members")
                .header("Authorization", bearer(owner)), "{\"email\":\"" + danEmail + "\",\"role\":\"developer\"}"))
            .andExpect(status().isCreated()).andReturn());

        mvc.perform(delete("/api/applications/" + id + "/team-members/" + member.get("teamMember").get("id").asText())
                .header("Authorization", bearer(owner)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(dan)))
            .andExpect(status().isNotFound());

        mvc.perform(delete("/api/applications/" + id).header("Authorization", bearer(owner)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/" + id).header("Authorization", bearer(owner)))
            .andExpect(status().isNotFound());
    }
}
