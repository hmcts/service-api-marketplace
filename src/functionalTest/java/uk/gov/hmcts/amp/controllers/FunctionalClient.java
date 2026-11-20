package uk.gov.hmcts.amp.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

final class FunctionalClient {

    static final String REQUESTING_USER_ID = "requestingUserId";
    static final String CORRELATION_ID = "X-Correlation-Id";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");
    private final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> get(final String path, final String... headers) throws Exception {
        return send(builder(path, headers).GET().build());
    }

    HttpResponse<String> post(final String path, final String body, final String... headers) throws Exception {
        return send(builder(path, headers)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
    }

    void signIn(final String objectId, final String email) throws Exception {
        assertThat(post("/session", "{\"objectId\":\"" + objectId + "\",\"email\":\"" + email + "\"}")
            .statusCode()).isEqualTo(200);
    }

    static JsonNode json(final HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    private HttpRequest.Builder builder(final String path, final String... headers) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(testUrl + path));
        return headers.length == 0 ? builder : builder.headers(headers);
    }

    private HttpResponse<String> send(final HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
