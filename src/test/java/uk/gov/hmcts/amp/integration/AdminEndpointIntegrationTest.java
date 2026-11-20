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
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class AdminEndpointIntegrationTest {

    private static final String USERS = "/admin/users";
    private static final String ADA = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String GRACE = "bbbbbbbb-1111-2222-3333-444444444444";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

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

    private long adaId;
    private long graceId;

    @BeforeEach
    void startClean() throws Exception {
        productionRequests.deleteAll();
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        adaId = signIn(ADA, "ada.lovelace@hmcts.net", "Ada Lovelace");
        graceId = signIn(GRACE, "grace.hopper@hmcts.net", "Grace Hopper");
        final UserEntity ada = users.findById(adaId).orElseThrow();
        ada.setRole(UserRole.ADMIN);
        users.save(ada);
    }

    @Test
    void an_admin_should_see_every_active_user_newest_first() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$[0].name").value("Grace Hopper"))
            .andExpect(jsonPath("$[0].id").value(graceId))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$[0].lastLoginAt").isNotEmpty())
            .andExpect(jsonPath("$[1].email").value("ada.lovelace@hmcts.net"));
    }

    @Test
    void suspended_user_should_be_left_out_of_the_list() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        grace.setStatus("SUSPENDED");
        users.save(grace);

        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("ada.lovelace@hmcts.net"));
    }

    @Test
    void the_list_should_count_each_users_applications() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        applications.save(application(grace, "Case Viewer", "client-1"));
        applications.save(application(grace, "Listings", "client-2"));

        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].applicationCount").value(2))
            .andExpect(jsonPath("$[1].applicationCount").value(0));
    }

    @Test
    void the_list_should_not_expose_identifiers_or_secrets() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", ADA))
            .andExpect(jsonPath("$[0].entraObjectId").doesNotExist())
            .andExpect(jsonPath("$[0].userReference").doesNotExist())
            .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused() throws Exception {
        mockMvc.perform(get(USERS).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void missing_or_unknown_caller_should_be_refused_as_elsewhere() throws Exception {
        mockMvc.perform(get(USERS)).andExpect(status().isBadRequest());
        mockMvc.perform(get(USERS).header("requestingUserId", "cccccccc-1111-2222-3333-444444444444"))
            .andExpect(status().isNotFound());
    }

    @Test
    void an_admin_should_see_one_user_with_their_applications_and_requests() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        final ApplicationEntity caseViewer = applications.save(application(grace, "Case Viewer", "client-1"));
        final ApplicationEntity listings = application(grace, "Listings", "client-2");
        listings.setCreatedAt(NOW.plusDays(1));
        applications.save(listings);
        applicationApis.save(api(caseViewer, "slc", "amp-user-case-viewer-slc"));
        applicationApis.save(api(caseViewer, "rcc", "amp-user-case-viewer-rcc"));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-OLDER001", "Old Org Ltd", NOW));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-NEWER001", "Example Legal Ltd",
            NOW.plusDays(2)));

        mockMvc.perform(get(USERS + "/" + graceId).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(graceId))
            .andExpect(jsonPath("$.name").value("Grace Hopper"))
            .andExpect(jsonPath("$.email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
            .andExpect(jsonPath("$.organisation").value("Example Legal Ltd"))
            .andExpect(jsonPath("$.applications.length()").value(2))
            .andExpect(jsonPath("$.applications[0].name").value("Case Viewer"))
            .andExpect(jsonPath("$.applications[0].apiCount").value(2))
            .andExpect(jsonPath("$.applications[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.applications[1].name").value("Listings"))
            .andExpect(jsonPath("$.applications[1].apiCount").value(0))
            .andExpect(jsonPath("$.productionRequests.length()").value(2))
            .andExpect(jsonPath("$.productionRequests[0].reference").value("PCR-NEWER001"))
            .andExpect(jsonPath("$.productionRequests[0].applicationName").value("Case Viewer"))
            .andExpect(jsonPath("$.productionRequests[0].status").value("SUBMITTED"))
            .andExpect(jsonPath("$.productionRequests[0].submittedAt").isNotEmpty())
            .andExpect(jsonPath("$.productionRequests[1].reference").value("PCR-OLDER001"));
    }

    @Test
    void user_with_no_production_requests_should_have_no_organisation() throws Exception {
        mockMvc.perform(get(USERS + "/" + graceId).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organisation").doesNotExist())
            .andExpect(jsonPath("$.applications.length()").value(0))
            .andExpect(jsonPath("$.productionRequests.length()").value(0));
    }

    @Test
    void suspended_user_should_still_be_shown_by_id() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        grace.setStatus("SUSPENDED");
        users.save(grace);

        mockMvc.perform(get(USERS + "/" + graceId).header("requestingUserId", ADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void an_unknown_user_should_be_not_found() throws Exception {
        mockMvc.perform(get(USERS + "/999999").header("requestingUserId", ADA))
            .andExpect(status().isNotFound());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused_one_user() throws Exception {
        mockMvc.perform(get(USERS + "/" + adaId).header("requestingUserId", GRACE))
            .andExpect(status().isForbidden());
    }

    @Test
    void one_user_should_not_expose_identifiers_or_secrets() throws Exception {
        applications.save(application(users.findById(graceId).orElseThrow(), "Case Viewer", "client-1"));

        mockMvc.perform(get(USERS + "/" + graceId).header("requestingUserId", ADA))
            .andExpect(jsonPath("$.entraObjectId").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.applications[0].clientId").doesNotExist())
            .andExpect(jsonPath("$.applications[0].secretHint").doesNotExist());
    }

    private static ApplicationApiEntity api(final ApplicationEntity application, final String shortCode,
                                            final String subscriptionName) {
        final ApplicationApiEntity api = new ApplicationApiEntity();
        api.setApplication(application);
        api.setShortCode(shortCode);
        api.setProductId("product-" + shortCode);
        api.setSubscriptionName(subscriptionName);
        api.setKeyHint("abc");
        api.setSubscribedAt(NOW);
        return api;
    }

    private static ProductionRequestEntity productionRequest(final UserEntity owner,
                                                             final ApplicationEntity application,
                                                             final String reference, final String organisation,
                                                             final LocalDateTime submittedAt) {
        final ProductionRequestEntity request = new ProductionRequestEntity();
        request.setUser(owner);
        request.setApplication(application);
        request.setApplicationName(application.getName());
        request.setReference(reference);
        request.setStatus("SUBMITTED");
        request.setSubmittedAt(submittedAt);
        request.setOrganisation(organisation);
        request.setGoLiveDate(LocalDate.of(2027, 3, 27));
        request.setCallVolume("low");
        return request;
    }

    private long signIn(final String oid, final String email, final String name) throws Exception {
        final String body = mockMvc.perform(post("/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectId\":\"" + oid + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static ApplicationEntity application(final UserEntity owner, final String name, final String clientId) {
        final ApplicationEntity application = new ApplicationEntity();
        application.setUser(owner);
        application.setName(name);
        application.setAppname(name.toLowerCase().replace(' ', '-'));
        application.setClientId(clientId);
        application.setObjectId("object-" + clientId);
        application.setSecretHint("Uc6");
        application.setSecretKeyId("key-" + clientId);
        application.setSecretExpiresAt(NOW.plusYears(2));
        application.setCreatedAt(NOW);
        return application;
    }
}