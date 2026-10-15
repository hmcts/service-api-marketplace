package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSmokeTest {

    // Fixed, so every run updates the same row rather than adding one.
    private static final String SMOKE_OID = "00000000-0000-0000-0000-00000000510e";

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void starting_a_session_for_the_smoke_user_should_return_them_from_the_database() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(testUrl + "/session"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"objectId\":\"" + SMOKE_OID
                + "\",\"email\":\"smoke-test@amp.example.com\",\"name\":\"Smoke Test\"}"))
            .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"entraObjectId\":\"" + SMOKE_OID + "\"", "\"status\":\"ACTIVE\"");
    }
}
