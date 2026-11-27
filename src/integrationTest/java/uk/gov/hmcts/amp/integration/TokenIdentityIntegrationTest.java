package uk.gov.hmcts.amp.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.amp.audit.AuditInterceptor;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.Instant;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "admin.auth.tenant-id=99999999-8888-7777-6666-555555555555",
    "admin.auth.audience=" + TokenIdentityIntegrationTest.AUDIENCE,
    "admin.auth.issuer-override=" + TokenIdentityIntegrationTest.ISSUER})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class TokenIdentityIntegrationTest {

    static final String AUDIENCE = "the-web-app";
    static final String ISSUER = "https://issuer.example/tenant/v2.0";

    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String NOBODY = "dddddddd-1111-2222-3333-444444444444";
    private static final String APPLICATIONS = "/applications";

    private static final RSAKey SIGNING_KEY = newKey("the-tenants-key");
    private static final RSAKey SOMEONE_ELSES_KEY = newKey("the-tenants-key");
    private static final WireMockServer KEYS = new WireMockServer(wireMockConfig().dynamicPort());

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

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditInterceptor.AUDIT_LOGGER);
    private final ListAppender<ILoggingEvent> auditLines = new ListAppender<>();

    @DynamicPropertySource
    static void serveTheSigningKeys(final DynamicPropertyRegistry registry) {
        if (!KEYS.isRunning()) {
            KEYS.start();
            KEYS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/keys"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
        }
        registry.add("admin.auth.jwk-set-uri-override", () -> KEYS.baseUrl() + "/keys");
    }

    @AfterAll
    static void stopServingKeys() {
        KEYS.stop();
    }

    @BeforeEach
    void startWithAdaSignedIn() throws Exception {
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + ADA + "\",\"email\":\"ada@example.com\",\"name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk());
        auditLines.start();
        auditLogger.addAppender(auditLines);
    }

    @AfterEach
    void stopCapturing() {
        auditLogger.detachAppender(auditLines);
    }

    @Test
    void token_should_act_for_its_user() throws Exception {
        mockMvc.perform(get(APPLICATIONS).header("Authorization", bearer(token(ADA))))
            .andExpect(status().isOk());
    }

    @Test
    void header_alone_should_be_refused() throws Exception {
        mockMvc.perform(get(APPLICATIONS).header("requestingUserId", ADA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void token_should_decide_who_is_acting_whatever_the_header_says() throws Exception {
        mockMvc.perform(get(APPLICATIONS).header("Authorization", bearer(token(NOBODY)))
                .header("requestingUserId", ADA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Sign in again."));
    }

    @Test
    void token_that_is_not_accepted_should_be_refused() throws Exception {
        final String forged = token(ADA, AUDIENCE, SOMEONE_ELSES_KEY, Instant.now().plusSeconds(600));

        mockMvc.perform(get(APPLICATIONS).header("Authorization", bearer(forged)).header("requestingUserId", ADA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void token_without_an_oid_should_be_refused() throws Exception {
        mockMvc.perform(get(APPLICATIONS).header("Authorization", bearer(token(null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void audit_line_should_name_the_tokens_user() throws Exception {
        mockMvc.perform(delete(APPLICATIONS + "/no-such-client").header("Authorization", bearer(token(ADA))))
            .andExpect(status().isNotFound());

        assertThat(auditLines.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_action", "CONSUMER_APPLICATION_DELETED")
            .containsEntry("audit_actor_oid", ADA));
    }

    @Test
    void admin_paths_should_still_require_the_admin_role() throws Exception {
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(token(ADA))))
            .andExpect(status().isForbidden());
    }

    private static String token(final String oid) {
        return token(oid, AUDIENCE, SIGNING_KEY, Instant.now().plusSeconds(600));
    }

    private static String token(final String oid, final String audience, final RSAKey key, final Instant expiry) {
        try {
            final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(audience)
                    .subject(ADA)
                    .claim("oid", oid)
                    .issueTime(Date.from(expiry.minusSeconds(3600)))
                    .notBeforeTime(Date.from(expiry.minusSeconds(3600)))
                    .expirationTime(Date.from(expiry))
                    .build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    private static RSAKey newKey(final String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
