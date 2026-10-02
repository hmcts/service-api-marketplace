package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Registers one real Microsoft Entra application per marketplace application - the same three
 * Graph calls proven in the Node.js prototype (create application, create service principal,
 * add a password), against the same throwaway {@code amp-client-onboarding-prototype}
 * identity. That identity is not the governed {@code amp-client-onboarding} service principal
 * from hmcts/external-entra-id#7; swapping it for the governed one later is a configuration
 * change here, not a code change (see the Jira backlog, Epic 1: "Replace the throwaway Graph
 * onboarding identity").
 *
 * <p>Service principal creation and password creation are retried: Graph's directory read
 * replicas can lag a few seconds behind the write that created the application, and the very
 * next call can 404 against a stale replica. This mirrors {@code graphRequestWithRetry} in the
 * prototype's routes.js.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntraAppRegistrationClient {

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Not final so tests can shorten it; production always waits the full two seconds.
    private Duration retryDelay = Duration.ofSeconds(2);

    // Identifies the shared hmctsextsbox CIAM tenant and doesn't change per
    // deploy, so it defaults rather than requiring a Key Vault entry, matching
    // the prototype's own convention for the same value.
    @Value("${ENTRA_TENANT_ID:d44f885c-4fac-47bf-afde-d7d861ec4d7b}")
    private String tenantId;

    @Value("${ENTRA_ONBOARDING_CLIENT_ID:NOT_SET}")
    private String onboardingClientId;

    @Value("${ENTRA_ONBOARDING_CLIENT_SECRET:NOT_SET}")
    private String onboardingClientSecret;

    public record Registration(String clientId, String clientSecret) {}

    public Registration register(final String applicationName) {
        requireConfigured();
        String accessToken = getOnboardingToken();
        JsonNode application = createApplication(accessToken, applicationName);
        String objectId = application.get("id").asText();
        String clientId = application.get("appId").asText();

        withRetry("create service principal", () -> {
            createServicePrincipal(accessToken, clientId);
            return null;
        });
        String clientSecret = withRetry("add password", () -> addPassword(accessToken, objectId));

        log.info("Registered Entra application {} for marketplace application '{}'", clientId, applicationName);
        return new Registration(clientId, clientSecret);
    }

    private void requireConfigured() {
        if (isNotSet(onboardingClientId) || isNotSet(onboardingClientSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Entra onboarding credential is not configured. Set ENTRA_ONBOARDING_CLIENT_ID "
                    + "and ENTRA_ONBOARDING_CLIENT_SECRET.");
        }
    }

    private boolean isNotSet(final String value) {
        return value == null || "NOT_SET".equals(value);
    }

    private String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getOnboardingToken() {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String form = "grant_type=client_credentials"
            + "&client_id=" + urlEncode(onboardingClientId)
            + "&client_secret=" + urlEncode(onboardingClientSecret)
            + "&scope=" + urlEncode("https://graph.microsoft.com/.default");
        JsonNode response = post(tokenUrl, form, "application/x-www-form-urlencoded", null);
        return response.get("access_token").asText();
    }

    private JsonNode createApplication(final String accessToken, final String applicationName) {
        String body = "{\"displayName\":" + objectMapper.valueToTree(applicationName) + "}";
        return post(GRAPH_BASE + "/applications", body, "application/json", accessToken);
    }

    private void createServicePrincipal(final String accessToken, final String clientId) {
        String body = "{\"appId\":" + objectMapper.valueToTree(clientId) + "}";
        post(GRAPH_BASE + "/servicePrincipals", body, "application/json", accessToken);
    }

    private String addPassword(final String accessToken, final String objectId) {
        String body = "{\"passwordCredential\":{\"displayName\":\"api-marketplace-generated\"}}";
        JsonNode response = post(GRAPH_BASE + "/applications/" + objectId + "/addPassword",
            body, "application/json", accessToken);
        return response.get("secretText").asText();
    }

    private interface RetryableCall<T> {
        T call();
    }

    private <T> T withRetry(final String description, final RetryableCall<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.call();
            } catch (final RuntimeException e) {
                lastFailure = e;
                log.warn("Graph call '{}' failed on attempt {}/{}: {}", description, attempt, MAX_ATTEMPTS,
                    e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep();
                }
            }
        }
        log.error("Entra Graph call '{}' did not succeed after {} attempts.", description, MAX_ATTEMPTS, lastFailure);
        throw registrationFailure(lastFailure);
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Interrupted while retrying Graph call.");
        }
    }

    private JsonNode post(final String url, final String body, final String contentType, final String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                // Logged in full - body is Graph's own error detail, which has no
                // business reaching whoever is registering an application.
                log.error("Entra Graph call to {} returned {}: {}", url, response.statusCode(), response.body());
                throw registrationFailure(null);
            }
            return objectMapper.readTree(response.body());
        } catch (final HttpTimeoutException e) {
            log.error("Entra Graph call to {} timed out.", url, e);
            throw registrationFailure(e);
        } catch (final ResponseStatusException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Entra Graph call to {} failed.", url, e);
            throw registrationFailure(e);
        }
    }

    private ResponseStatusException registrationFailure(final Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Could not register the application. Please try again.", cause);
    }
}
