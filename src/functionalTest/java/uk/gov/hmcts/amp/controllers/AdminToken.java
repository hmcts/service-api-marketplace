package uk.gov.hmcts.amp.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An Entra ID token for an account holding amp_admin, as the web app would send it.
 *
 * <p>ADMIN_FUNCTIONAL_TOKEN is used as it is when set. Otherwise the token is requested from Entra with
 * the admin test account's email and password (AMP_TEST_ADMIN_EMAIL, AMP_TEST_ADMIN_PASSWORD) for the web
 * app's client (ADMIN_TOKEN_CLIENT_ID, and ADMIN_TOKEN_CLIENT_SECRET where the client needs one) from
 * ADMIN_TOKEN_URL. With neither, there is no token and the admin cases are skipped.
 */
final class AdminToken {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static String token;
    private static boolean requested;

    private AdminToken() {
    }

    static synchronized String get() throws Exception {
        if (!requested) {
            token = resolve();
            requested = true;
        }
        return token;
    }

    private static String resolve() throws Exception {
        final String given = env("ADMIN_FUNCTIONAL_TOKEN");
        if (given != null) {
            return given;
        }
        final String email = env("AMP_TEST_ADMIN_EMAIL");
        final String password = env("AMP_TEST_ADMIN_PASSWORD");
        final String tokenUrl = env("ADMIN_TOKEN_URL");
        final String clientId = env("ADMIN_TOKEN_CLIENT_ID");
        if (email == null || password == null || tokenUrl == null || clientId == null) {
            return null;
        }

        final Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", clientId);
        form.put("scope", "openid");
        form.put("username", email);
        form.put("password", password);
        final String clientSecret = env("ADMIN_TOKEN_CLIENT_SECRET");
        if (clientSecret != null) {
            form.put("client_secret", clientSecret);
        }

        final HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        final JsonNode body = JSON.readTree(response.body());
        if (response.statusCode() != 200 || !body.hasNonNull("id_token")) {
            // Entra's error code and description name the problem without repeating the credentials
            throw new IllegalStateException("Entra gave no admin token (" + response.statusCode() + "): "
                + body.path("error").asText() + " " + body.path("error_description").asText());
        }
        return body.get("id_token").asText();
    }

    private static String env(final String name) {
        final String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String encode(final Map<String, String> form) {
        return form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(e.getValue(), UTF_8))
            .collect(Collectors.joining("&"));
    }
}
