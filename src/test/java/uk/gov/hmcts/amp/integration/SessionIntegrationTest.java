package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class SessionIntegrationTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void startWithNoUsers() {
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
    }

    @Test
    void first_sign_in_should_create_an_active_user() throws Exception {
        signIn(OID, "{\"email\":\"Joe@Example.com\",\"name\":\"Joe Bloggs\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.entraObjectId").value(OID))
            .andExpect(jsonPath("$.email").value("joe@example.com"))
            .andExpect(jsonPath("$.name").value("Joe Bloggs"))
            .andExpect(jsonPath("$.userReference").value(org.hamcrest.Matchers.matchesPattern("user\\d{3,}")))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        assertThat(users()).isEqualTo(1);
    }

    @Test
    void signing_in_again_should_update_the_same_user_from_entra() throws Exception {
        final String first = signIn(OID, "{\"email\":\"joe@example.com\",\"name\":\"Joe\"}")
            .andReturn().getResponse().getContentAsString();

        signIn(OID.toUpperCase(), "{\"email\":\"joseph@example.com\",\"name\":\"Joseph Bloggs\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idIn(first)))
            .andExpect(jsonPath("$.email").value("joseph@example.com"))
            .andExpect(jsonPath("$.name").value("Joseph Bloggs"));

        assertThat(users()).isEqualTo(1);
    }

    @Test
    void blank_name_should_be_stored_as_empty() throws Exception {
        signIn(OID, "{\"email\":\"joe@example.com\",\"name\":\"  \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(""));
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
        final UserEntity user = users.findByEntraObjectId(OID).orElseThrow();
        user.setStatus("SUSPENDED");
        users.saveAndFlush(user);

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
        signIn(OID, "{\"name\":\"Joe\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Enter a valid email address."));
    }

    @Test
    void the_same_api_should_not_be_subscribed_twice_and_deleting_an_application_removes_its_apis() throws Exception {
        signIn(OID, "{\"email\":\"joe@example.com\"}").andExpect(status().isOk());
        final UserEntity owner = users.findByEntraObjectId(OID).orElseThrow();
        final ApplicationEntity application = applications.saveAndFlush(application(owner));
        applicationApis.saveAndFlush(api(application, "amp-user001-case-viewer-slc"));

        assertThat(rejected(() -> applicationApis.saveAndFlush(
            api(application, "amp-user001-case-viewer-slc-2")))).isTrue();

        applications.delete(application);
        applications.flush();
        assertThat(applicationApis.count()).isZero();
    }

    private ResultActions signIn(final String oid, final String body) throws Exception {
        final String withObjectId = "{\"objectId\":\"" + oid + "\"," + body.substring(1);
        return mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON).content(withObjectId));
    }

    private int users() {
        return (int) users.count();
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

    private ApplicationEntity application(final UserEntity owner) {
        final ApplicationEntity application = new ApplicationEntity();
        application.setUser(owner);
        application.setName("Case viewer");
        application.setAppname("case-viewer");
        application.setClientId("client-1");
        application.setObjectId("object-1");
        application.setSecretHint("Uc6");
        application.setSecretKeyId("key-1");
        application.setSecretExpiresAt(LocalDateTime.now());
        application.setCreatedAt(LocalDateTime.now());
        return application;
    }

    private ApplicationApiEntity api(final ApplicationEntity application, final String subscriptionName) {
        final ApplicationApiEntity api = new ApplicationApiEntity();
        api.setApplication(application);
        api.setShortCode("slc");
        api.setProductId("cp-crime-schedulingandlisting");
        api.setSubscriptionName(subscriptionName);
        api.setKeyHint("abc");
        api.setSubscribedAt(LocalDateTime.now());
        return api;
    }

}
