package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSmokeTest {

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void the_catalogue_should_list_the_apis_the_migrations_add() throws Exception {
        var request = HttpRequest.newBuilder().uri(URI.create(testUrl + "/products")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"shortCode\":\"slc\"", "\"shortCode\":\"rcc\"");
    }
}
