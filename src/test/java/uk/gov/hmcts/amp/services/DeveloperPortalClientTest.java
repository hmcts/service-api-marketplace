package uk.gov.hmcts.amp.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeveloperPortalClientTest {

    private static final String API_NAME = "common-platform-api-refdata-court-hearing-court-houses";
    private static final String EXPORT_PATH = "/developer/apis/" + API_NAME;
    private static final String SPEC_PATH = "/api-export/a-document";

    private WireMockServer portal;
    private DeveloperPortalClient client;

    @BeforeEach
    void setUp() {
        portal = new WireMockServer(wireMockConfig().dynamicPort());
        portal.start();
        client = new DeveloperPortalClient(RestClient.builder().baseUrl(portal.baseUrl()).build());
    }

    @AfterEach
    void tearDown() {
        portal.stop();
    }

    @Test
    void the_description_the_published_spec_carries_should_be_returned() {
        portalReturnsLinkTo(SPEC_PATH);
        specIs("""
            openapi: 3.0.0
            info:
              title: Common Platform API Refdata Court Hearing Court Houses
              description: |
                Provides information about court buildings and the rooms within them.

                Returns the building name, postal address, and room details for a given court.
            """);

        assertThat(client.description(API_NAME))
            .startsWith("Provides information about court buildings")
            .contains("postal address");
    }

    @Test
    void an_api_whose_specification_has_no_description_should_answer_nothing_rather_than_fail() {
        portalReturnsLinkTo(SPEC_PATH);
        specIs("""
            openapi: 3.0.1
            info:
              title: Crime Scheduling and Listing Schedule API (slc)
              description: \'\'
            paths: { }
            """);

        assertThat(client.description(API_NAME)).isNull();
    }

    @Test
    void the_filename_quotes_azure_adds_should_not_stop_the_link_being_followed() {
        // Azure quotes the filename in rscd without escaping it, and a quote is not legal in a
        // query, so the link the portal hands back is not a URI until it is encoded.
        final String withQuotes = SPEC_PATH + "?sv=2024-08-04&rscd=attachment%3B+filename%3D"
            + "\"Crime Ref Data Court Hearing Courthouse API (rcc).yaml\"&sig=zq0bJR74";
        portal.stubFor(get(urlPathEqualTo(EXPORT_PATH)).willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"link\":\"" + portal.baseUrl()
                + withQuotes.replace("\"", "\\\"") + "\"}")));
        specIs("""
            openapi: 3.0.0
            info:
              description: Provides information about court buildings.
            """);

        assertThat(client.description(API_NAME)).isEqualTo("Provides information about court buildings.");
    }

    @Test
    void the_portal_answering_an_error_should_say_it_could_not_be_read() {
        portal.stubFor(get(urlPathEqualTo(EXPORT_PATH)).willReturn(aResponse().withStatus(503)));

        assertThatBadGateway().hasMessageContaining("could not be read");
    }

    @Test
    void an_export_response_with_no_link_should_not_be_followed() {
        portal.stubFor(get(urlPathEqualTo(EXPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThatBadGateway().hasMessageContaining("published no specification");
        assertThat(portal.getAllServeEvents()).hasSize(1);
    }

    @Test
    void an_api_with_no_name_in_api_management_should_not_be_asked_for() {
        assertThatThrownBy(() -> client.description(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("no API Management name");
        assertThat(portal.getAllServeEvents()).isEmpty();
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertThatBadGateway() {
        return assertThatThrownBy(() -> client.description(API_NAME))
            .isInstanceOfSatisfying(ResponseStatusException.class, refused ->
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private void portalReturnsLinkTo(final String path) {
        portal.stubFor(get(urlPathEqualTo(EXPORT_PATH)).willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"link\":\"" + portal.baseUrl() + path + "\"}")));
    }

    private void specIs(final String document) {
        portal.stubFor(get(urlPathEqualTo(SPEC_PATH)).willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/yaml")
            .withBody(document)));
    }
}
