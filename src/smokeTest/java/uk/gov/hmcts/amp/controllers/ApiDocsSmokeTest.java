package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsSmokeTest {

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void the_openapi_definition_the_security_scan_reads_should_be_published() throws Exception {
        var request = HttpRequest.newBuilder().uri(URI.create(testUrl + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\":\"3.", "\"/applications\"");
    }
}
