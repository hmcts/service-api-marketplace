package uk.gov.hmcts.amp.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;
import uk.gov.hmcts.amp.audit.AuditInterceptor;

import static uk.gov.hmcts.amp.integration.SignedIn.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "admin.auth.tenant-id=99999999-8888-7777-6666-555555555555",
    "admin.auth.audience=the-web-app"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class AuditIntegrationTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CORRELATION_ID = "c0ffee12-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private UserRepository users;

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditInterceptor.AUDIT_LOGGER);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void startWithNoUsersAndCaptureAuditLines() {
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void sign_in_should_write_a_success_audit_line_with_the_correlation_id() throws Exception {
        signIn().andExpect(status().isOk());

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_action", "USER_SIGNED_IN")
            .containsEntry("audit_actor_oid", OID)
            .containsEntry("audit_target", OID)
            .containsEntry("audit_outcome", "SUCCESS")
            .containsEntry("audit_status", "200")
            .containsEntry("audit_endpoint", "POST /session")
            .containsEntry("X-Correlation-Id", CORRELATION_ID));
    }

    @Test
    void deleting_an_unknown_application_should_write_a_failure_audit_line() throws Exception {
        signIn();
        appender.list.clear();

        mockMvc.perform(delete("/applications/no-such-client").with(signedInAs(OID)))
            .andExpect(status().isNotFound());

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_action", "CONSUMER_APPLICATION_DELETED")
            .containsEntry("audit_actor_oid", OID)
            .containsEntry("audit_target", "no-such-client")
            .containsEntry("audit_outcome", "FAILURE")
            .containsEntry("audit_status", "404")
            .containsEntry("audit_endpoint", "DELETE /applications/{clientId}"));
    }

    @Test
    void an_admin_viewing_users_should_write_an_audit_line_for_the_list_and_for_the_user() throws Exception {
        signIn();
        final UserEntity admin = users.findByEntraObjectId(OID).orElseThrow();
        users.save(admin);
        appender.list.clear();

        mockMvc.perform(adminGet("/admin/users")).andExpect(status().isOk());
        mockMvc.perform(adminGet("/admin/users/" + admin.getId())).andExpect(status().isOk());

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getMDCPropertyMap())
            .containsEntry("audit_action", "ADMIN_USERS_LISTED")
            .containsEntry("audit_actor_oid", OID)
            .containsEntry("audit_outcome", "SUCCESS")
            .containsEntry("audit_endpoint", "GET /admin/users");
        assertThat(appender.list.get(1).getMDCPropertyMap())
            .containsEntry("audit_action", "ADMIN_USER_VIEWED")
            .containsEntry("audit_target", String.valueOf(admin.getId()))
            .containsEntry("audit_outcome", "SUCCESS")
            .containsEntry("audit_endpoint", "GET /admin/users/{userId}");
    }

    @Test
    void an_admin_using_the_support_lookup_should_write_an_audit_line_for_the_list_and_for_the_user()
        throws Exception {
        signIn();
        final UserEntity admin = users.findByEntraObjectId(OID).orElseThrow();
        users.save(admin);
        appender.list.clear();

        mockMvc.perform(adminGet("/admin/users/lookup")).andExpect(status().isOk());
        mockMvc.perform(adminGet("/admin/users/lookup/" + OID)).andExpect(status().isOk());

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getMDCPropertyMap())
            .containsEntry("audit_action", "ADMIN_USERS_LISTED")
            .containsEntry("audit_endpoint", "GET /admin/users/lookup");
        assertThat(appender.list.get(1).getMDCPropertyMap())
            .containsEntry("audit_action", "ADMIN_USER_VIEWED")
            .containsEntry("audit_target", OID)
            .containsEntry("audit_endpoint", "GET /admin/users/lookup/{userId}");
    }

    @Test
    void non_admin_trying_to_list_users_should_write_a_failure_audit_line() throws Exception {
        signIn();
        appender.list.clear();

        mockMvc.perform(nonAdminGet("/admin/users").with(jwt().jwt(token -> token.claim("oid", OID))))
            .andExpect(status().isForbidden());

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_action", "ADMIN_ACCESS_DENIED")
            .containsEntry("audit_actor_oid", OID)
            .containsEntry("audit_outcome", "FAILURE")
            .containsEntry("audit_status", "403")
            .containsEntry("audit_endpoint", "GET /admin/users"));
    }

    private ResultActions signIn() throws Exception {
        return mockMvc.perform(post("/session")
            .header("X-Correlation-Id", CORRELATION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"objectId\":\"" + OID + "\",\"email\":\"joe@example.com\",\"name\":\"Joe Bloggs\"}"));
    }

    private static MockHttpServletRequestBuilder adminGet(final String uri) {
        return get(uri).with(jwt().jwt(token -> token.claim("oid", OID))
            .authorities(new SimpleGrantedAuthority("ROLE_amp_admin")));
    }


    /** A token from someone signed in who does not hold the role; the chain refuses it. */
    private static MockHttpServletRequestBuilder nonAdminGet(final String uri) {
        return get(uri).with(jwt());
    }

}
