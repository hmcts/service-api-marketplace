package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HealthSmokeTest {

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void the_api_health_check_should_say_ok() throws Exception {
        var response = get("/api/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"ok\"");
    }

    @Test
    void the_actuator_health_check_should_report_the_database_up() throws Exception {
        var response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"db\":{", "\"status\":\"UP\"");
        assertThat(response.body()).doesNotContain("\"status\":\"DOWN\"");
    }

    private HttpResponse<String> get(final String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(URI.create(testUrl + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
