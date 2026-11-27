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
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "admin.auth.tenant-id=99999999-8888-7777-6666-555555555555",
    "admin.auth.audience=the-web-app"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class AdminEndpointIntegrationTest {

    private static final String USERS = "/admin/users";
    private static final String APPLICATIONS = "/admin/applications";
    private static final String REQUESTS = "/admin/production-requests";
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
    }

    @Test
    void an_admin_should_see_every_active_user_newest_first() throws Exception {
        mockMvc.perform(adminGet(USERS))
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

        mockMvc.perform(adminGet(USERS))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("ada.lovelace@hmcts.net"));
    }

    @Test
    void the_list_should_count_each_users_applications() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        applications.save(application(grace, "Case Viewer", "client-1"));
        applications.save(application(grace, "Listings", "client-2"));

        mockMvc.perform(adminGet(USERS))
            .andExpect(jsonPath("$[0].applicationCount").value(2))
            .andExpect(jsonPath("$[1].applicationCount").value(0));
    }

    @Test
    void the_list_should_not_expose_identifiers_or_secrets() throws Exception {
        mockMvc.perform(adminGet(USERS))
            .andExpect(jsonPath("$[0].entraObjectId").doesNotExist())
            .andExpect(jsonPath("$[0].userReference").doesNotExist())
            .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused() throws Exception {
        mockMvc.perform(nonAdminGet(USERS))
            .andExpect(status().isForbidden());
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

        mockMvc.perform(adminGet(USERS + "/" + graceId))
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
        mockMvc.perform(adminGet(USERS + "/" + graceId))
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

        mockMvc.perform(adminGet(USERS + "/" + graceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void an_unknown_user_should_be_not_found() throws Exception {
        mockMvc.perform(adminGet(USERS + "/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused_one_user() throws Exception {
        mockMvc.perform(nonAdminGet(USERS + "/" + adaId))
            .andExpect(status().isForbidden());
    }

    @Test
    void one_user_should_not_expose_identifiers_or_secrets() throws Exception {
        applications.save(application(users.findById(graceId).orElseThrow(), "Case Viewer", "client-1"));

        mockMvc.perform(adminGet(USERS + "/" + graceId))
            .andExpect(jsonPath("$.entraObjectId").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.applications[0].clientId").doesNotExist())
            .andExpect(jsonPath("$.applications[0].secretHint").doesNotExist());
    }

    @Test
    void an_admin_should_see_every_application_newest_first() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        final ApplicationEntity caseViewer = applications.save(application(grace, "Case Viewer", "client-1"));
        final ApplicationEntity listings = application(users.findById(adaId).orElseThrow(), "Listings", "client-2");
        listings.setCreatedAt(NOW.plusDays(1));
        applications.save(listings);
        applicationApis.save(api(caseViewer, "slc", "amp-user-case-viewer-slc"));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-ABCD2345", "Example Legal Ltd", NOW));

        mockMvc.perform(adminGet(APPLICATIONS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Listings"))
            .andExpect(jsonPath("$[0].owner.id").value(adaId))
            .andExpect(jsonPath("$[0].owner.name").value("Ada Lovelace"))
            .andExpect(jsonPath("$[0].apiCount").value(0))
            .andExpect(jsonPath("$[0].productionRequested").value(false))
            .andExpect(jsonPath("$[1].id").value(caseViewer.getId()))
            .andExpect(jsonPath("$[1].name").value("Case Viewer"))
            .andExpect(jsonPath("$[1].owner.email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$[1].apiCount").value(1))
            .andExpect(jsonPath("$[1].createdAt").isNotEmpty())
            .andExpect(jsonPath("$[1].productionRequested").value(true))
            .andExpect(jsonPath("$[1].clientId").doesNotExist())
            .andExpect(jsonPath("$[1].secretHint").doesNotExist());
    }

    @Test
    void an_admin_should_see_one_application_with_its_apis_and_latest_request() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        final ApplicationEntity caseViewer = applications.save(application(grace, "Case Viewer", "client-1"));
        applicationApis.save(api(caseViewer, "slc", "amp-user-case-viewer-slc"));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-OLDER001", "Old Org Ltd", NOW));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-NEWER001", "Example Legal Ltd",
            NOW.plusDays(2)));

        mockMvc.perform(adminGet(APPLICATIONS + "/" + caseViewer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(caseViewer.getId()))
            .andExpect(jsonPath("$.name").value("Case Viewer"))
            .andExpect(jsonPath("$.clientId").value("client-1"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.owner.id").value(graceId))
            .andExpect(jsonPath("$.owner.name").value("Grace Hopper"))
            .andExpect(jsonPath("$.owner.email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$.apis.length()").value(1))
            .andExpect(jsonPath("$.apis[0]").value("Scheduling and Listing Court Schedule"))
            .andExpect(jsonPath("$.productionRequest.reference").value("PCR-NEWER001"))
            .andExpect(jsonPath("$.productionRequest.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.productionRequest.submittedAt").isNotEmpty())
            .andExpect(jsonPath("$.secretHint").doesNotExist())
            .andExpect(jsonPath("$.objectId").doesNotExist());
    }

    @Test
    void application_with_no_request_should_have_none() throws Exception {
        final ApplicationEntity caseViewer =
            applications.save(application(users.findById(graceId).orElseThrow(), "Case Viewer", "client-1"));

        mockMvc.perform(adminGet(APPLICATIONS + "/" + caseViewer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apis.length()").value(0))
            .andExpect(jsonPath("$.productionRequest").doesNotExist());
    }

    @Test
    void an_unknown_application_should_be_not_found() throws Exception {
        mockMvc.perform(adminGet(APPLICATIONS + "/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused_the_applications() throws Exception {
        mockMvc.perform(nonAdminGet(APPLICATIONS))
            .andExpect(status().isForbidden());
        mockMvc.perform(nonAdminGet(APPLICATIONS + "/1"))
            .andExpect(status().isForbidden());
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

    @Test
    void an_admin_should_see_every_production_request_oldest_first() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        final ApplicationEntity caseViewer = applications.save(application(grace, "Case Viewer", "client-1"));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-NEWER001", "Example Legal Ltd",
            NOW.plusDays(2)));
        productionRequests.save(productionRequest(grace, caseViewer, "PCR-OLDER001", "Old Org Ltd", NOW));

        mockMvc.perform(adminGet(REQUESTS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].reference").value("PCR-OLDER001"))
            .andExpect(jsonPath("$[0].applicationId").value(caseViewer.getId()))
            .andExpect(jsonPath("$[0].applicationName").value("Case Viewer"))
            .andExpect(jsonPath("$[0].organisation").value("Old Org Ltd"))
            .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
            .andExpect(jsonPath("$[0].submittedAt").isNotEmpty())
            .andExpect(jsonPath("$[1].reference").value("PCR-NEWER001"));
    }

    @Test
    void an_admin_should_see_one_production_request_with_who_asked_and_every_answer() throws Exception {
        final UserEntity grace = users.findById(graceId).orElseThrow();
        final ApplicationEntity caseViewer = applications.save(application(grace, "Case Viewer", "client-1"));
        applicationApis.save(api(caseViewer, "slc", "amp-user-case-viewer-slc"));
        final ProductionRequestEntity request =
            productionRequest(grace, caseViewer, "PCR-ABCD2345", "Northshire Police", NOW);
        request.setDataRequest("Daily hearing lists");
        request.setOrganisationType("other-government");
        request.setDataRecipients(java.util.List.of("own-team", "supplier"));
        request.setDataRecipientsExplanation("Our supplier hosts the system");
        request.setDataCombined("no");
        productionRequests.save(request);

        mockMvc.perform(adminGet(REQUESTS + "/PCR-ABCD2345"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reference").value("PCR-ABCD2345"))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.requester.id").value(graceId))
            .andExpect(jsonPath("$.requester.name").value("Grace Hopper"))
            .andExpect(jsonPath("$.requester.email").value("grace.hopper@hmcts.net"))
            .andExpect(jsonPath("$.organisation").value("Northshire Police"))
            .andExpect(jsonPath("$.applicationId").value(caseViewer.getId()))
            .andExpect(jsonPath("$.applicationName").value("Case Viewer"))
            .andExpect(jsonPath("$.apis[0]").value("Scheduling and Listing Court Schedule"))
            .andExpect(jsonPath("$.goLiveDate").value("2027-03-27"))
            .andExpect(jsonPath("$.callVolume").value("low"))
            .andExpect(jsonPath("$.dataRequest").value("Daily hearing lists"))
            .andExpect(jsonPath("$.organisationType").value("other-government"))
            .andExpect(jsonPath("$.dataRecipients[1]").value("supplier"))
            .andExpect(jsonPath("$.dataRecipientsExplanation").value("Our supplier hosts the system"))
            .andExpect(jsonPath("$.dataCombined").value("no"))
            .andExpect(jsonPath("$.requester.entraObjectId").doesNotExist())
            .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    @Test
    void an_unknown_production_request_should_be_not_found() throws Exception {
        mockMvc.perform(adminGet(REQUESTS + "/PCR-NOTHERE1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void user_who_is_not_an_admin_should_be_refused_the_production_requests() throws Exception {
        mockMvc.perform(nonAdminGet(REQUESTS))
            .andExpect(status().isForbidden());
        mockMvc.perform(nonAdminGet(REQUESTS + "/PCR-ABCD2345"))
            .andExpect(status().isForbidden());
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

    private static MockHttpServletRequestBuilder adminGet(final String uri) {
        return get(uri).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_amp_admin")));
    }


    /** A token from someone signed in who does not hold the role; the chain refuses it. */
    private static MockHttpServletRequestBuilder nonAdminGet(final String uri) {
        return get(uri).with(jwt());
    }

}