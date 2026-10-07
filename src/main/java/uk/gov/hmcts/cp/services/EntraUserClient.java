package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
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

/**
 * Creates the person's user in Microsoft Entra External ID when they register, so that they can sign in with
 * Entra: one Microsoft Graph call, {@code POST /users}, for a "local account" signed in by email address.
 *
 * <p>Off unless {@code ACCOUNT_IDENTITY=entra}; anything else but {@code local} stops the service starting,
 * rather than quietly registering people nowhere.
 *
 * <p>It uses its own credential ({@code ENTRA_USER_ONBOARDING_CLIENT_ID} / {@code _SECRET}), not the one that
 * registers applications. Creating users needs a user-write Graph permission, a far broader power than the
 * application-owner permission that credential holds, so it should be a different identity that can be granted
 * and withdrawn on its own. Until it exists, enabling this answers 503.
 *
 * <p>Not tried against the real Graph. The request follows Graph's documented form for a local account, and is
 * exercised by unit tests and the stand-in in demo/; creating a real user in the tenant is for a person to do.
 * The password the person chose is sent to Graph and is never logged; neither is the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntraUserClient {

    static final String LOCAL = "local";
    static final String ENTRA = "entra";
    static final String GENERIC_FAILURE = "Could not create the account. Please try again.";

    /** Entra already has a user with this email address. */
    public static class AlreadyExists extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Entra refused the password, under its own complexity rules. */
    public static class PasswordRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Not final so tests can set it; the default keeps registration exactly as it was.
    @Value("${ACCOUNT_IDENTITY:local}")
    private String mode = LOCAL;

    @Value("${ENTRA_TENANT_ID:d44f885c-4fac-47bf-afde-d7d861ec4d7b}")
    private String tenantId;

    // The tenant's own domain, which a local account's identity names as its issuer.
    @Value("${ENTRA_TENANT_DOMAIN:hmctsextsbox.onmicrosoft.com}")
    private String tenantDomain;

    @Value("${ENTRA_USER_ONBOARDING_CLIENT_ID:NOT_SET}")
    private String clientId;

    @Value("${ENTRA_USER_ONBOARDING_CLIENT_SECRET:NOT_SET}")
    private String clientSecret;

    // The real Microsoft addresses, unless a local demo or a test points them at a stand-in.
    @Value("${ENTRA_GRAPH_BASE_URL:https://graph.microsoft.com/v1.0}")
    private String graphBase = "https://graph.microsoft.com/v1.0";

    @Value("${ENTRA_LOGIN_BASE_URL:https://login.microsoftonline.com}")
    private String loginBase = "https://login.microsoftonline.com";

    @PostConstruct
    void requireKnownMode() {
        String normalised = normalised();
        if (!LOCAL.equals(normalised) && !ENTRA.equals(normalised)) {
            throw new IllegalStateException("ACCOUNT_IDENTITY must be 'local' or 'entra', not '" + mode + "'");
        }
    }

    public boolean enabled() {
        return ENTRA.equals(normalised());
    }

    /** Creates the user and returns its Entra object id. */
    public String createUser(final String firstName, final String lastName, final String email,
        final String password) {
        requireConfigured();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("accountEnabled", true);
        body.put("displayName", firstName + " " + lastName);
        body.put("givenName", firstName);
        body.put("surname", lastName);
        body.putArray("identities").addObject()
            .put("signInType", "emailAddress")
            .put("issuer", tenantDomain)
            .put("issuerAssignedId", email);
        body.putObject("passwordProfile")
            .put("password", password)
            .put("forceChangePasswordNextSignIn", false);
        body.put("passwordPolicies", "DisablePasswordExpiration");

        String accessToken = getToken();
        JsonNode created = send("POST", graphBase + "/users", body.toString(), "application/json", accessToken,
            false);
        String objectId = created.path("id").asText("");
        if (objectId.isBlank()) {
            log.error("Entra created a user but returned no object id");
            throw failure(null);
        }
        log.info("Created Entra user {}", objectId);
        return objectId;
    }

    /**
     * Deletes a user made by {@link #createUser}. A user that is already gone is not a failure.
     */
    public void deleteUser(final String objectId) {
        requireConfigured();
        send("DELETE", graphBase + "/users/" + urlEncode(objectId), null, null, getToken(), true);
        log.info("Deleted Entra user {}", objectId);
    }

    /**
     * For clean-up after a failure: never throws, so it cannot hide the failure being handled.
     */
    public void undoCreate(final String objectId) {
        try {
            deleteUser(objectId);
        } catch (RuntimeException cleanupFailure) {
            log.error("Registration failed and Entra user {} could not be deleted - remove it manually",
                objectId, cleanupFailure);
        }
    }

    private void requireConfigured() {
        if (isNotSet(clientId) || isNotSet(clientSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Entra user credential is not configured. Set ENTRA_USER_ONBOARDING_CLIENT_ID "
                    + "and ENTRA_USER_ONBOARDING_CLIENT_SECRET.");
        }
    }

    private boolean isNotSet(final String value) {
        return value == null || "NOT_SET".equals(value);
    }

    private String normalised() {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getToken() {
        String tokenUrl = loginBase + "/" + tenantId + "/oauth2/v2.0/token";
        String form = "grant_type=client_credentials"
            + "&client_id=" + urlEncode(clientId)
            + "&client_secret=" + urlEncode(clientSecret)
            + "&scope=" + urlEncode("https://graph.microsoft.com/.default");
        return send("POST", tokenUrl, form, "application/x-www-form-urlencoded", null, false)
            .get("access_token").asText();
    }

    private JsonNode send(final String method, final String url, final String body, final String contentType,
        final String bearerToken, final boolean missingIsFine) {
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
            int status = response.statusCode();
            if (missingIsFine && status == HttpStatus.NOT_FOUND.value()) {
                return objectMapper.createObjectNode();
            }
            if (status >= 300) {
                // Graph's own error text goes to the log only, never to the caller. The request is never logged:
                // it carries the person's password.
                log.error("Entra Graph call to {} returned {}: {}", url, status, response.body());
                if (status == HttpStatus.FORBIDDEN.value()) {
                    log.error("403 creating a user: the credential in ENTRA_USER_ONBOARDING_CLIENT_ID probably "
                        + "lacks a user-write Graph permission (User.ReadWrite.All)");
                }
                throw classify(status, response.body());
            }
            // A successful DELETE answers 204 with no body.
            return response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        } catch (final HttpTimeoutException e) {
            log.error("Entra Graph call to {} timed out.", url, e);
            throw failure(e);
        } catch (final InterruptedException e) {
            // Restore the flag rather than swallow it, so a shutting-down pod still sees the interrupt.
            Thread.currentThread().interrupt();
            log.error("Entra Graph call to {} was interrupted.", url, e);
            throw failure(e);
        } catch (final ResponseStatusException | AlreadyExists | PasswordRejected e) {
            throw e;
        } catch (final Exception e) {
            log.error("Entra Graph call to {} failed.", url, e);
            throw failure(e);
        }
    }

    // Two refusals the person can do something about get their own answer; everything else is generic.
    private RuntimeException classify(final int status, final String body) {
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == HttpStatus.BAD_REQUEST.value()) {
            if (text.contains("objectconflict") || text.contains("already exists")) {
                return new AlreadyExists();
            }
            if (text.contains("password") && (text.contains("complexity") || text.contains("policy"))) {
                return new PasswordRejected();
            }
        }
        return failure(null);
    }

    private ResponseStatusException failure(final Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, GENERIC_FAILURE, cause);
    }
}
