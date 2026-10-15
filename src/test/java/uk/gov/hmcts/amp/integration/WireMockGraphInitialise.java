package uk.gov.hmcts.amp.integration;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** WireMock in place of Microsoft Graph. From entra-app-registration-demo in service-hmcts-springboot-demo. */
public class WireMockGraphInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String API_CLIENT_ID = "99999999-9999-9999-9999-999999999999";
    public static final String OBJECT_ID = "11111111-1111-1111-1111-111111111111";
    public static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    public static final String ROLE = "slc.read";

    private static final String KEY_ID = "33333333-3333-3333-3333-333333333333";
    private static final String API_SP_ID = "44444444-4444-4444-4444-444444444444";
    private static final String CONSUMER_SP_ID = "55555555-5555-5555-5555-555555555555";
    private static final String ROLE_ID = "66666666-6666-6666-6666-666666666666";

    private static final WireMockServer WIREMOCK_GRAPH = new WireMockServer(wireMockConfig().dynamicPort());

    public static WireMockServer wireMockGraph() {
        return WIREMOCK_GRAPH;
    }

    public static String assignedTo() {
        return "/servicePrincipals/" + API_SP_ID + "/appRoleAssignedTo";
    }

    public static void stubApplicationList() {
        WIREMOCK_GRAPH.stubFor(get(urlPathEqualTo("/applications")).willReturn(json("""
            {"value":[{"id":"%s","appId":"%s","displayName":"Team Alpha",
                       "createdDateTime":"2026-01-02T03:04:05Z"}]}""".formatted(OBJECT_ID, CLIENT_ID))));
    }

    /** A second registration, because Entra issues a different id each time and client_id is unique. */
    public static void stubRegistrationAs(final String objectId, final String clientId) {
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/applications")).willReturn(json(
            "{\"id\":\"" + objectId + "\",\"appId\":\"" + clientId + "\"}")));
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(json("{\"id\":\"sp-2\"}")));
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/applications/" + objectId + "/addPassword"))
            .willReturn(json("{\"keyId\":\"88888888-8888-8888-8888-888888888888\","
                + "\"secretText\":\"another-secret\"}")));
    }

    public static void stubRegistration() {
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/applications")).willReturn(json(
            "{\"id\":\"" + OBJECT_ID + "\",\"appId\":\"" + CLIENT_ID + "\"}")));
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/servicePrincipals")).willReturn(json("{\"id\":\"sp-1\"}")));
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo("/applications/" + OBJECT_ID + "/addPassword"))
            .willReturn(json("{\"keyId\":\"" + KEY_ID + "\",\"secretText\":\"the-secret-shown-once\"}")));
    }

    public static void stubServicePrincipals() {
        WIREMOCK_GRAPH.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + CLIENT_ID + "'"))
            .willReturn(json("{\"value\":[{\"id\":\"" + CONSUMER_SP_ID + "\"}]}")));
        WIREMOCK_GRAPH.stubFor(get(urlPathEqualTo("/servicePrincipals"))
            .withQueryParam("$filter", equalTo("appId eq '" + API_CLIENT_ID + "'"))
            .willReturn(json("{\"value\":[{\"id\":\"" + API_SP_ID + "\"}]}")));
        WIREMOCK_GRAPH.stubFor(get(urlPathEqualTo("/servicePrincipals/" + API_SP_ID)).willReturn(json("""
            {"id":"%s","appRoles":[{"id":"%s","value":"%s"}]}""".formatted(API_SP_ID, ROLE_ID, ROLE))));
    }

    public static void stubHeldRoles() {
        WIREMOCK_GRAPH.stubFor(get(urlPathEqualTo("/servicePrincipals/" + CONSUMER_SP_ID + "/appRoleAssignments"))
            .willReturn(json("{\"value\":[{\"id\":\"assign-1\",\"appRoleId\":\"" + ROLE_ID + "\"}]}")));
    }

    public static void stubRoleAssignment() {
        WIREMOCK_GRAPH.stubFor(post(urlPathEqualTo(assignedTo())).willReturn(json("{\"id\":\"assign-1\"}")));
    }

    public static void stubApplicationDelete() {
        WIREMOCK_GRAPH.stubFor(delete(urlPathMatching("/applications/.*")).willReturn(aResponse().withStatus(204)));
    }

    private static ResponseDefinitionBuilder json(final String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        if (!WIREMOCK_GRAPH.isRunning()) {
            WIREMOCK_GRAPH.start();
        }
        applicationContext.getBeanFactory().registerSingleton("graphServiceClient", clientPointedAtWireMock());
    }

    // A token the mock never checks, so the test needs no credential and reaches no network.
    private GraphServiceClient clientPointedAtWireMock() {
        TokenCredential noRealCredential = request ->
            Mono.just(new AccessToken("a-graph-token", OffsetDateTime.MAX));
        GraphServiceClient sdk = new GraphServiceClient(noRealCredential, "https://graph.microsoft.com/.default");
        sdk.getRequestAdapter().setBaseUrl(WIREMOCK_GRAPH.baseUrl());
        return sdk;
    }
}
