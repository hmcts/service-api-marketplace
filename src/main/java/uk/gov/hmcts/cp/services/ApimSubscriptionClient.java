package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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
public class ApimSubscriptionClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String ARM_BASE = "https://management.azure.com";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${APIM_TENANT_ID:NOT_SET}")
    private String tenantId;

    @Value("${APIM_CLIENT_ID:NOT_SET}")
    private String clientId;

    @Value("${APIM_CLIENT_SECRET:NOT_SET}")
    private String clientSecret;

    @Value("${APIM_SUBSCRIPTION_ID:NOT_SET}")
    private String azureSubscriptionId;

    @Value("${APIM_RESOURCE_GROUP:NOT_SET}")
    private String resourceGroup;

    @Value("${APIM_SERVICE_NAME:NOT_SET}")
    private String serviceName;

    public record Subscription(String publisherId, String subscriptionKey) {}

    public Subscription createSubscription(final String applicationName, final String productId) {
        requireConfigured();
        String accessToken = getArmToken();
        String subscriptionName = subscriptionNameFor(applicationName);
        JsonNode response = putSubscription(accessToken, subscriptionName, productId, applicationName);
        String primaryKey = response.get("properties").get("primaryKey").asText();
        return new Subscription(productId, primaryKey);
    }

    private void requireConfigured() {
        if (isNotSet(tenantId) || isNotSet(clientId) || isNotSet(clientSecret)
            || isNotSet(azureSubscriptionId) || isNotSet(resourceGroup) || isNotSet(serviceName)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "APIM credential is not configured. Set APIM_TENANT_ID, APIM_CLIENT_ID, APIM_CLIENT_SECRET, "
                    + "APIM_SUBSCRIPTION_ID, APIM_RESOURCE_GROUP and APIM_SERVICE_NAME.");
        }
    }

    private boolean isNotSet(final String value) {
        return value == null || "NOT_SET".equals(value);
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
            + "&client_id=" + clientId
            + "&client_secret=" + clientSecret
            + "&scope=" + "https://management.azure.com/.default";
        JsonNode response = send("POST", tokenUrl, form, "application/x-www-form-urlencoded", null);
        return response.get("access_token").asText();
    }

    private JsonNode putSubscription(final String accessToken, final String subscriptionName,
        final String productId, final String applicationName) {
        String url = String.format(Locale.ROOT,
            "%s/subscriptions/%s/resourceGroups/%s/providers/Microsoft.ApiManagement/service/%s"
                + "/subscriptions/%s?api-version=2022-08-01",
            ARM_BASE, azureSubscriptionId, resourceGroup, serviceName, subscriptionName);
        String body = "{\"properties\":{\"scope\":\"/products/" + productId + "\",\"displayName\":"
            + objectMapper.valueToTree(applicationName + " (" + productId + ")") + "}}";
        return send("PUT", url, body, "application/json", accessToken);
    }

    private JsonNode send(final String method, final String url, final String body, final String contentType,
        final String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", contentType)
            .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    String.format(Locale.ROOT, "APIM call to %s returned %d: %s",
                        url, response.statusCode(), response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (final HttpTimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "APIM call to " + url + " timed out.", e);
        } catch (final ResponseStatusException e) {
            throw e;
        } catch (final Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "APIM call to " + url + " failed.", e);
        }
    }
}
