package uk.gov.hmcts.amp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** WireMock in place of the developer portal, which is where every description now comes from. */
public class WireMockPortalInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String PORTAL = "https://amp-portal.example";
    public static final String SLC = "common-platform-api-scheduling-and-listing-court-schedule";
    public static final String RCC = "common-platform-api-refdata-court-hearing-court-houses";
    public static final String PCD = "crime-prosecution-case-details";

    private static final WireMockServer WIREMOCK_PORTAL = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        WIREMOCK_PORTAL.start();
        publish(SLC, """
            Returns the court hearing schedule for a given case.

            Shows when and where hearings are listed to take place.
            """);
        publish(RCC, """
            Provides information about court buildings and the rooms within them.

            Returns the building name, postal address, and room details for a given court.
            """);
        publish(PCD, """
            Returns the details of a prosecution case for a given case URN.
            """);
    }

    @Override
    public void initialize(final ConfigurableApplicationContext context) {
        context.getBeanFactory().registerSingleton("developerPortal",
            RestClient.builder().baseUrl(WIREMOCK_PORTAL.baseUrl()).build());
    }

    private static void publish(final String apimApiName, final String description) {
        final String specPath = "/api-export/" + apimApiName;
        WIREMOCK_PORTAL.stubFor(get(urlPathEqualTo("/developer/apis/" + apimApiName))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"link\":\"" + WIREMOCK_PORTAL.baseUrl() + specPath + "\"}")));
        WIREMOCK_PORTAL.stubFor(get(urlPathEqualTo(specPath))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/yaml")
                .withBody("openapi: 3.0.0\ninfo:\n  title: An API\n  description: |\n"
                    + description.indent(4))));
    }
}
