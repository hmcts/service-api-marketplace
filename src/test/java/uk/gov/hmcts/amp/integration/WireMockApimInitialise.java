package uk.gov.hmcts.amp.integration;

import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** WireMock in place of API Management. From apim-subscription-key-demo in service-hmcts-springboot-demo. */
public class WireMockApimInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String RESOURCE_GROUP = "rg-sps-platform-sbox";
    public static final String SERVICE_NAME = "sps-api-mgmt-sbox";
    public static final String INSTANCE = "/subscriptions/azure-sub/resourceGroups/" + RESOURCE_GROUP
        + "/providers/Microsoft.ApiManagement/service/" + SERVICE_NAME;

    private static final String EXISTS = "exists";
    private static final String DELETED = "deleted";

    private static final WireMockServer WIREMOCK_APIM = new WireMockServer(wireMockConfig().dynamicPort());

    public static WireMockServer wireMockApim() {
        return WIREMOCK_APIM;
    }

    /** A captured 200 from src/test/resources/__files, named for the subscription under test. */
    public static ResponseDefinitionBuilder json(final String fixture, final String name) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody(captured(fixture).replace("team-alpha", name));
    }

    // A scenario holds the state, so "gone after delete" follows from the delete, not from a stub.
    public static void stubSubscriptionLifecycle(final String name) {
        String url = INSTANCE + "/subscriptions/" + name;
        String scenario = "subscription " + name;

        WIREMOCK_APIM.stubFor(get(urlPathEqualTo(url)).inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(404)));

        WIREMOCK_APIM.stubFor(put(urlPathEqualTo(url)).inScenario(scenario)
            .willReturn(json("subscription-created.json", name))
            .willSetStateTo(EXISTS));

        WIREMOCK_APIM.stubFor(get(urlPathEqualTo(url)).inScenario(scenario)
            .whenScenarioStateIs(EXISTS)
            .willReturn(json("subscription.json", name)));

        WIREMOCK_APIM.stubFor(post(urlPathEqualTo(url + "/listSecrets")).inScenario(scenario)
            .whenScenarioStateIs(EXISTS)
            .willReturn(json("secrets.json", name)));

        WIREMOCK_APIM.stubFor(delete(urlPathEqualTo(url)).inScenario(scenario)
            .whenScenarioStateIs(EXISTS)
            .willReturn(aResponse().withStatus(200))
            .willSetStateTo(DELETED));

        WIREMOCK_APIM.stubFor(post(urlPathEqualTo(url + "/listSecrets")).inScenario(scenario)
            .whenScenarioStateIs(DELETED)
            .willReturn(aResponse().withStatus(404)));

        WIREMOCK_APIM.stubFor(get(urlPathEqualTo(url)).inScenario(scenario)
            .whenScenarioStateIs(DELETED)
            .willReturn(aResponse().withStatus(404)));
    }

    /** API Management answering with one status, whatever is asked of that subscription. */
    public static void stubSubscriptionAnswering(final String name, final int status) {
        String url = INSTANCE + "/subscriptions/" + name;
        WIREMOCK_APIM.stubFor(put(urlPathEqualTo(url)).willReturn(aResponse().withStatus(status)));
        WIREMOCK_APIM.stubFor(get(urlPathEqualTo(url)).willReturn(aResponse().withStatus(status)));
        WIREMOCK_APIM.stubFor(post(urlPathEqualTo(url + "/listSecrets"))
            .willReturn(aResponse().withStatus(status)));
    }

    /** Every subscription the instance holds, shaped like a captured response. */
    public static void stubSubscriptionList(final String... names) {
        String entries = Stream.of(names).map(name -> """
            {"id":"%1$s/subscriptions/%2$s","name":"%2$s","type":"Microsoft.ApiManagement/service/subscriptions",
             "properties":{"allowTracing":false,"createdDate":"2026-10-08T15:35:59.2877274Z",
              "displayName":"%2$s","scope":"%1$s/products/example-product","state":"active"}}"""
            .formatted(INSTANCE, name)).collect(Collectors.joining(","));
        WIREMOCK_APIM.stubFor(get(urlPathEqualTo(INSTANCE + "/subscriptions")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"value\":[" + entries + "],\"count\":" + names.length + "}")));
    }

    private static String captured(final String fixture) {
        try (InputStream file = WireMockApimInitialise.class.getResourceAsStream("/__files/" + fixture)) {
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException cannotRead) {
            throw new IllegalStateException("Could not read " + fixture, cannotRead);
        }
    }

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        if (!WIREMOCK_APIM.isRunning()) {
            WIREMOCK_APIM.start();
        }
        applicationContext.getBeanFactory()
            .registerSingleton("apiManagementManager", managerPointedAtWireMock());
    }

    // No bearer policy: that policy refuses plain HTTP, and leaving it out is why no credential is needed.
    private ApiManagementManager managerPointedAtWireMock() {
        AzureEnvironment wireMockApim = new AzureEnvironment(Map.of(
            "resourceManagerEndpointUrl", WIREMOCK_APIM.baseUrl(),
            "activeDirectoryEndpointUrl", WIREMOCK_APIM.baseUrl(),
            "activeDirectoryResourceId", WIREMOCK_APIM.baseUrl()));
        return ApiManagementManager.authenticate(new HttpPipelineBuilder().build(),
            new AzureProfile("a-tenant", "azure-sub", wireMockApim));
    }
}
