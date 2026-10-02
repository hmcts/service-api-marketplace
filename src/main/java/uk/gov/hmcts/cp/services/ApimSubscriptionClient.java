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
import java.util.Locale;
import java.util.UUID;

/**
 * Issues one real Azure API Management Subscription Key per (application, API) pair, against
 * the shared {@code sps-api-mgmt-sbox} instance - the same ARM call proven in the Node.js
 * prototype: {@code PUT .../subscriptions/{name}} scoped to the API's Product, which returns
 * the key inline in the same response.
 *
 * <p>The credential used here is the real gap tracked in the Jira backlog (Epic 1:
 * "Provision a corporate-tenant service principal for APIM") - the intended service principal
 * in the corporate tenant needs Application Administrator rights that haven't been granted
 * yet. Rather than substitute a developer's own az-cli login (fine for a one-off manual test,
 * not something to build into a service that other people's requests will run through), this
 * fails fast with a clear 503 until APIM_CLIENT_ID / APIM_CLIENT_SECRET are actually set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApimSubscriptionClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String ARM_BASE = "https://management.azure.com";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Only the credential itself needs a secret - these identify the shared
    // sandbox instance (sps-api-mgmt-sbox) and don't change per deploy, so
    // they default rather than requiring a Key Vault entry each, matching the
    // prototype's own convention for the same values.
    @Value("${APIM_TENANT_ID:531ff96d-0ae9-462a-8d2d-bec7c0b42082}")
    private String tenantId;

    @Value("${APIM_CLIENT_ID:NOT_SET}")
    private String clientId;

    @Value("${APIM_CLIENT_SECRET:NOT_SET}")
    private String clientSecret;

    @Value("${APIM_SUBSCRIPTION_ID:bd2864ed-4f3e-45ed-9c6a-8d179674bab1}")
    private String azureSubscriptionId;

    @Value("${APIM_RESOURCE_GROUP:rg-sps-platform-sbox}")
    private String resourceGroup;

    @Value("${APIM_SERVICE_NAME:sps-api-mgmt-sbox}")
    private String serviceName;

    public record Subscription(String publisherId, String subscriptionKey, String subscriptionName) {}

    public Subscription createSubscription(final String applicationName, final String productId) {
        requireConfigured();
        String accessToken = getArmToken();
        String subscriptionName = subscriptionNameFor(applicationName);
        JsonNode response = putSubscription(accessToken, subscriptionName, productId, applicationName);
        String primaryKey = response.get("properties").get("primaryKey").asText();
        return new Subscription(productId, primaryKey, subscriptionName);
    }

    /**
     * Revokes a subscription issued by {@link #createSubscription}, so that a registration that
     * fails after some keys were issued does not leave live keys that nobody was ever given.
     */
    public void deleteSubscription(final String subscriptionName) {
        requireConfigured();
        String accessToken = getArmToken();
        send("DELETE", subscriptionUrl(subscriptionName), null, null, accessToken);
        log.info("Deleted APIM subscription {}", subscriptionName);
    }

    private void requireConfigured() {
        if (isNotSet(clientId) || isNotSet(clientSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "APIM credential is not configured. Set APIM_CLIENT_ID and APIM_CLIENT_SECRET.");
        }
    }

    private boolean isNotSet(final String value) {
        return value == null || "NOT_SET".equals(value);
    }

    private String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Kept short and unique per call rather than deterministic from (applicationName, apiShortCode):
    // a developer adding the same API twice to the same application - after deleting the first
    // subscription - must not collide with the old, now-revoked name.
    private String subscriptionNameFor(final String applicationName) {
        String slug = applicationName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return slug + "-" + suffix;
    }

    private String getArmToken() {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String form = "grant_type=client_credentials"
            + "&client_id=" + urlEncode(clientId)
            + "&client_secret=" + urlEncode(clientSecret)
            + "&scope=" + urlEncode("https://management.azure.com/.default");
        JsonNode response = send("POST", tokenUrl, form, "application/x-www-form-urlencoded", null);
        return response.get("access_token").asText();
    }

    private JsonNode putSubscription(final String accessToken, final String subscriptionName,
        final String productId, final String applicationName) {
        String url = subscriptionUrl(subscriptionName);
        String body = "{\"properties\":{\"scope\":\"/products/" + productId + "\",\"displayName\":"
            + objectMapper.valueToTree(applicationName + " (" + productId + ")") + "}}";
        return send("PUT", url, body, "application/json", accessToken);
    }

    private String subscriptionUrl(final String subscriptionName) {
        return String.format(Locale.ROOT,
            "%s/subscriptions/%s/resourceGroups/%s/providers/Microsoft.ApiManagement/service/%s"
                + "/subscriptions/%s?api-version=2022-08-01",
            ARM_BASE, azureSubscriptionId, resourceGroup, serviceName, subscriptionName);
    }

    private JsonNode send(final String method, final String url, final String body, final String contentType,
        final String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                // Logged in full (URL includes the Azure subscription/resource group;
                // body is Azure's own error detail) - neither belongs in a response to
                // whoever is registering an application.
                log.error("APIM call to {} returned {}: {}", url, response.statusCode(), response.body());
                throw apiKeyFailure(null);
            }
            // A successful DELETE answers with no body.
            return response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        } catch (final HttpTimeoutException e) {
            log.error("APIM call to {} timed out.", url, e);
            throw apiKeyFailure(e);
        } catch (final InterruptedException e) {
            // Restore the flag rather than swallow it, so a shutting-down pod still sees the interrupt.
            Thread.currentThread().interrupt();
            log.error("APIM call to {} was interrupted.", url, e);
            throw apiKeyFailure(e);
        } catch (final ResponseStatusException e) {
            throw e;
        } catch (final Exception e) {
            log.error("APIM call to {} failed.", url, e);
            throw apiKeyFailure(e);
        }
    }

    private ResponseStatusException apiKeyFailure(final Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Could not issue an API key. Please try again.", cause);
    }
}
