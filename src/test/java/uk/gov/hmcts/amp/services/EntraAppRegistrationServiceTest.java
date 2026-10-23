package uk.gov.hmcts.amp.services;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.ApiGrant;
import uk.gov.hmcts.amp.domain.AppRegistration;

import java.time.OffsetDateTime;
import java.util.List;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real Graph SDK against a mock Entra, so the calls it makes are visible. */
class EntraAppRegistrationServiceTest {

    private static final String OBJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String KEY_ID = "33333333-3333-3333-3333-333333333333";
    private static final String API_SP_ID = "44444444-4444-4444-4444-444444444444";
    private static final String CONSUMER_SP_ID = "55555555-5555-5555-5555-555555555555";
    private static final String ROLE_ID = "66666666-6666-6666-6666-666666666666";
    private static final String API_CLIENT_ID = "99999999-9999-9999-9999-999999999999";
    private static final List<ApiGrant> SLC_GRANT = List.of(new ApiGrant("slc", API_CLIENT_ID, "app.read"));
    private static final String ASSIGNED_TO = "/servicePrincipals/" + API_SP_ID + "/appRoleAssignedTo";
    private static final int ATTEMPTS = 4;

    private WireMockServer mockEntra;
    private EntraAppRegistrationService service;

    @BeforeEach
    void startMockEntra() {
        mockEntra = new WireMockServer(wireMockConfig().dynamicPort());
        mockEntra.start();

        // A token the mock never checks, so the test needs no credential and reaches no network.
        TokenCredential noRealCredential = request ->
            Mono.just(new AccessToken("a-graph-token", OffsetDateTime.MAX));
        GraphServiceClient sdk = new GraphServiceClient(noRealCredential, "https://graph.microsoft.com/.default");
        sdk.getRequestAdapter().setBaseUrl(mockEntra.baseUrl());

        service = new EntraAppRegistrationService(sdk, properties("a-secret"));
    }

    @AfterEach
    void stopMockEntra() {
        mockEntra.stop();
    }

    @Test
    void registering_an_application_should_make_the_three_graph_calls_and_return_the_secret() {
        stubRegistrationCalls();

        AppRegistration registered = service.register("Team Alpha", List.of());

        assertThat(registered.objectId()).isEqualTo(OBJECT_ID);
        assertThat(registered.clientId()).isEqualTo(CLIENT_ID);
        assertThat(registered.clientSecret()).isEqualTo("the-secret-shown-once");
        assertThat(registered.keyId()).isEqualTo(KEY_ID);

        mockEntra.verify(postRequestedFor(urlPathEqualTo("/applications"))
            .withRequestBody(equalToJson("{\"displayName\":\"Team Alpha\"}", true, true)));
        // The service principal is created from the client id, not the object id.
        mockEntra.verify(postRequestedFor(urlPathEqualTo("/servicePrincipals"))
            .withRequestBody(equalToJson("{\"appId\":\"" + CLIENT_ID + "\"}", true, true)));
        // addPassword addresses the application by object id, which only the create response carries.
        mockEntra.verify(postRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID + "/addPassword")));
    }

    @Test
    void registering_with_roles_should_grant_them_in_the_same_call() {
        stubRegistrationCalls();
        stubServicePrincipalLookups();
        stubApiRoles();
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(json("{\"id\":\"assignment-1\"}")));

        AppRegistration registered = service.register("Team Alpha", SLC_GRANT);

        assertThat(registered.roles()).containsExactly("slc");
        // Assigned on the API's principal, naming the consumer as principalId - not the other way round.
        mockEntra.verify(postRequestedFor(urlPathEqualTo(ASSIGNED_TO)).withRequestBody(equalToJson("""
            {"principalId":"%s","resourceId":"%s","appRoleId":"%s"}"""
            .formatted(CONSUMER_SP_ID, API_SP_ID, ROLE_ID), true, true)));
    }

    @Test
    void assignment_refused_while_the_principal_is_new_should_be_retried_rather_than_rolled_back() {
        stubRegistrationCalls();
        stubServicePrincipalLookups();
        stubApiRoles();
        // Entra refuses an assignment against a service principal seconds old, for a while.
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).inScenario("assignable")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(400).withBody("Not a valid reference update."))
            .willSetStateTo("assignable"));
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).inScenario("assignable")
            .whenScenarioStateIs("assignable")
            .willReturn(json("{\"id\":\"assignment-1\"}")));

        assertThat(service.register("Team Alpha", SLC_GRANT).roles()).containsExactly("slc");

        mockEntra.verify(2, postRequestedFor(urlPathEqualTo(ASSIGNED_TO)));
        // The application survives, rather than being removed as an orphan.
        mockEntra.verify(0, deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
    }

    @Test
    void registering_when_a_graph_permission_is_missing_should_fail_at_once_rather_than_wait() {
        stubRegistrationCalls();
        stubServicePrincipalLookups();
        stubApiRoles();
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> service.register("Team Alpha", SLC_GRANT))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
            .hasMessageContaining("AppRoleAssignment.ReadWrite.All");

        // Waiting never grants a permission, so the attempts would hold the request open for nothing.
        mockEntra.verify(1, postRequestedFor(urlPathEqualTo(ASSIGNED_TO)));
    }

    @Test
    void granting_a_role_should_assign_it_on_the_apis_service_principal() {
        stubServicePrincipalLookups();
        stubApiRoles();
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(json("{\"id\":\"assignment-1\"}")));

        service.grantApiAccess(CLIENT_ID, API_CLIENT_ID, "app.read");

        // Assigned on the API's principal, naming the consumer as principalId - not the other way round.
        mockEntra.verify(postRequestedFor(urlPathEqualTo(ASSIGNED_TO)).withRequestBody(equalToJson("""
            {"principalId":"%s","resourceId":"%s","appRoleId":"%s"}"""
            .formatted(CONSUMER_SP_ID, API_SP_ID, ROLE_ID), true, true)));
    }

    @Test
    void role_the_api_does_not_offer_should_say_what_it_does_offer() {
        stubServicePrincipalLookups();
        stubApiRoles();

        assertThatThrownBy(() -> service.grantApiAccess(CLIENT_ID, API_CLIENT_ID, "nonsense"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
            .hasMessageContaining("app.read");
    }

    @Test
    void refused_assignment_should_name_the_permission_that_is_missing() {
        stubServicePrincipalLookups();
        stubApiRoles();
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> service.grantApiAccess(CLIENT_ID, API_CLIENT_ID, "app.read"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                // Not 503: a missing consent grant is not temporary, and 503 invites endless retrying.
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
            .hasMessageContaining("AppRoleAssignment.ReadWrite.All");

        // The caller decides whether to retry, so the service asks exactly once.
        mockEntra.verify(1, postRequestedFor(urlPathEqualTo(ASSIGNED_TO)));
    }

    @Test
    void reading_one_application_should_name_the_roles_it_holds() {
        stubApplicationList();
        stubServicePrincipalLookups();
        stubApiRoles();
        stubAssignments("""
            {"value":[{"id":"assign-1","appRoleId":"%s","resourceId":"%s"}]}"""
            .formatted(ROLE_ID, API_SP_ID));

        AppRegistration detail = service.get(CLIENT_ID);

        // Graph answers with the role's id, and names the resource it belongs to.
        assertThat(detail.roles()).containsExactly("app.read");
        assertThat(detail.clientId()).isEqualTo(CLIENT_ID);
        assertThat(detail.clientSecret()).isNull();
    }

    @Test
    void reading_an_application_that_is_not_there_should_be_not_found() {
        mockEntra.stubFor(get(urlPathEqualTo("/applications")).willReturn(json("{\"value\":[]}")));

        assertThatThrownBy(() -> service.get(CLIENT_ID))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listing_applications_should_return_what_is_needed_to_recognise_and_remove_them() {
        mockEntra.stubFor(get(urlPathEqualTo("/applications")).willReturn(json("""
            {"value":[
              {"id":"obj-2","appId":"app-2","displayName":"newer","createdDateTime":"2026-06-07T08:09:10Z"},
              {"id":"obj-1","appId":"app-1","displayName":"older","createdDateTime":"2026-01-02T03:04:05Z"}
            ]}""")));

        List<AppRegistration> found = service.list();

        assertThat(found).extracting(AppRegistration::displayName).containsExactly("older", "newer");
        assertThat(found.getFirst().objectId()).isEqualTo("obj-1");
        assertThat(found.getFirst().created()).isNotNull();
        // $orderby on applications is refused by Graph unless the request opts into advanced queries.
        mockEntra.verify(getRequestedFor(urlPathEqualTo("/applications")).withoutQueryParam("$orderby"));
    }

    @Test
    void listing_applications_should_follow_graphs_paging_rather_than_stop_at_the_first_page() {
        mockEntra.stubFor(get(urlPathEqualTo("/applications")).willReturn(json("""
            {"value":[{"id":"obj-1","appId":"app-1","displayName":"page one"}],
             "@odata.nextLink":"%s/applications?$skiptoken=abc"}""".formatted(mockEntra.baseUrl()))));
        mockEntra.stubFor(get(urlPathEqualTo("/applications")).withQueryParam("$skiptoken", equalTo("abc"))
            .willReturn(json("{\"value\":[{\"id\":\"obj-2\",\"appId\":\"app-2\",\"displayName\":\"page two\"}]}")));

        List<AppRegistration> found = service.list();

        assertThat(found).extracting(AppRegistration::displayName).containsExactly("page one", "page two");
    }

    @Test
    void deleting_an_application_should_find_its_object_id_from_the_client_id() {
        stubApplicationList();
        mockEntra.stubFor(delete(urlPathMatching("/applications/.*")).willReturn(aResponse().withStatus(204)));

        service.delete(CLIENT_ID);

        // The caller names the client id; Graph needs the object id, which is looked up rather than asked for.
        mockEntra.verify(deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
    }

    @Test
    void service_principal_that_404s_on_a_stale_replica_should_be_retried_until_it_appears() {
        stubCreateApplication();
        stubAddPassword();
        // Graph's read replicas lag the write that created the application: the first attempt misses it.
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).inScenario("replication")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(404))
            .willSetStateTo("replicated"));
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).inScenario("replication")
            .whenScenarioStateIs("replicated")
            .willReturn(json("{\"id\":\"sp-1\"}")));

        assertThat(service.register("Team Alpha", List.of()).clientId()).isEqualTo(CLIENT_ID);

        mockEntra.verify(2, postRequestedFor(urlPathEqualTo("/servicePrincipals")));
    }

    @Test
    void service_principal_not_yet_replicated_should_be_waited_for_rather_than_failing() {
        stubRegistrationCalls();
        stubApiRoles();
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + API_CLIENT_ID + "'"))
            .willReturn(json("{\"value\":[{\"id\":\"" + API_SP_ID + "\"}]}")));
        // The consumer's own principal was made seconds ago: the first read misses it.
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + CLIENT_ID + "'"))
            .inScenario("replication").whenScenarioStateIs(STARTED)
            .willReturn(json("{\"value\":[]}"))
            .willSetStateTo("replicated"));
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + CLIENT_ID + "'"))
            .inScenario("replication").whenScenarioStateIs("replicated")
            .willReturn(json("{\"value\":[{\"id\":\"" + CONSUMER_SP_ID + "\"}]}")));
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(json("{\"id\":\"assignment-1\"}")));

        assertThat(service.register("Team Alpha", SLC_GRANT).roles()).containsExactly("slc");
    }

    @Test
    void service_principal_that_never_appears_should_give_up_rather_than_retry_forever() {
        stubCreateApplication();
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> service.register("Team Alpha", List.of()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Could not register the application");

        mockEntra.verify(ATTEMPTS, postRequestedFor(urlPathEqualTo("/servicePrincipals")));
    }

    @Test
    void role_that_cannot_be_granted_should_take_the_new_application_away_again() {
        stubRegistrationCalls();
        stubServicePrincipalLookups();
        stubApiRoles();
        mockEntra.stubFor(post(urlPathEqualTo(ASSIGNED_TO)).willReturn(aResponse().withStatus(403)));
        mockEntra.stubFor(delete(urlPathMatching("/applications/.*")).willReturn(aResponse().withStatus(204)));

        assertThatThrownBy(() -> service.register("Team Alpha", SLC_GRANT))
            .isInstanceOf(ResponseStatusException.class);

        // A registration whose secret was shown but whose access failed is worse than none.
        mockEntra.verify(deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
    }

    @Test
    void registration_that_fails_after_the_application_exists_should_not_leave_it_behind() {
        stubCreateApplication();
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(aResponse().withStatus(404)));
        mockEntra.stubFor(delete(urlPathMatching("/applications/.*")).willReturn(aResponse().withStatus(204)));

        assertThatThrownBy(() -> service.register("Team Alpha", List.of()))
            .isInstanceOf(ResponseStatusException.class);

        mockEntra.verify(deleteRequestedFor(urlPathEqualTo("/applications/" + OBJECT_ID)));
    }

    @Test
    void cleanup_that_also_fails_should_still_report_the_registration_failure() {
        stubCreateApplication();
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(aResponse().withStatus(404)));
        mockEntra.stubFor(delete(urlPathMatching("/applications/.*")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> service.register("Team Alpha", List.of()))
            .isInstanceOf(ResponseStatusException.class)
            // Not "could not delete": the caller needs to know registration failed.
            .hasMessageContaining("Could not register the application")
            .satisfies(failure -> assertThat(failure.getSuppressed()).isNotEmpty());
    }

    @Test
    void an_unconfigured_credential_should_be_unavailable_rather_than_reaching_graph() {
        EntraAppRegistrationService unconfigured =
            new EntraAppRegistrationService(null, properties("NOT_SET"));

        assertThatThrownBy(unconfigured::list)
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private EntraProperties properties(final String secret) {
        return new EntraProperties("a-tenant", "an-onboarding-app", secret,
            Map.of("slc", API_CLIENT_ID), ATTEMPTS, 1);
    }

    private void stubRegistrationCalls() {
        stubCreateApplication();
        mockEntra.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(json("{\"id\":\"sp-1\"}")));
        stubAddPassword();
    }

    private void stubCreateApplication() {
        mockEntra.stubFor(post(urlPathEqualTo("/applications"))
            .willReturn(json("{\"id\":\"" + OBJECT_ID + "\",\"appId\":\"" + CLIENT_ID + "\"}")));
    }

    private void stubAddPassword() {
        mockEntra.stubFor(post(urlPathEqualTo("/applications/" + OBJECT_ID + "/addPassword"))
            .willReturn(json("{\"keyId\":\"" + KEY_ID + "\",\"secretText\":\"the-secret-shown-once\"}")));
    }

    private void stubApplicationList() {
        mockEntra.stubFor(get(urlPathEqualTo("/applications")).willReturn(json("""
            {"value":[{"id":"%s","appId":"%s","displayName":"Team Alpha"}]}"""
            .formatted(OBJECT_ID, CLIENT_ID))));
    }

    private void stubServicePrincipalLookups() {
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + CLIENT_ID + "'"))
            .willReturn(json("{\"value\":[{\"id\":\"" + CONSUMER_SP_ID + "\"}]}")));
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + API_CLIENT_ID + "'"))
            .willReturn(json("{\"value\":[{\"id\":\"" + API_SP_ID + "\"}]}")));
    }

    private void stubApiRoles() {
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals/" + API_SP_ID)).willReturn(json("""
            {"id":"%s","appRoles":[{"id":"%s","value":"app.read"}]}""".formatted(API_SP_ID, ROLE_ID))));
    }

    private void stubAssignments(final String body) {
        mockEntra.stubFor(get(urlPathEqualTo("/servicePrincipals/" + CONSUMER_SP_ID + "/appRoleAssignments"))
            .willReturn(json(body)));
    }

    private ResponseDefinitionBuilder json(final String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
