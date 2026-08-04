package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SampleFunctionalTest {

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void calling_root_should_return_welcome_message() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
            .uri(URI.create(testUrl))
            .GET()
            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("Welcome");
    }
}
