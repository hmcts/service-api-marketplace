package uk.gov.hmcts.cp.services;

import com.microsoft.graph.applications.item.addpassword.AddPasswordPostRequestBody;
import com.microsoft.graph.models.AppRole;
import com.microsoft.graph.models.AppRoleAssignment;
import com.microsoft.graph.models.AppRoleAssignmentCollectionResponse;
import com.microsoft.graph.models.Application;
import com.microsoft.graph.models.ApplicationCollectionResponse;
import com.microsoft.graph.models.PasswordCredential;
import com.microsoft.graph.models.ServicePrincipal;
import com.microsoft.graph.models.ServicePrincipalCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.EntraProperties;
import uk.gov.hmcts.cp.domain.AppRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

/**
 * Registers an Entra application and grants it app roles on the marketplace API, so the credentials it
 * hands back carry a roles claim that APIM's validate-jwt policy accepts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntraAppRegistrationService {

    private interface GraphCall<T> {
        T call();
    }

    private final GraphServiceClient graph;
    private final EntraProperties config;

    public List<AppRegistration> list() {
        requireCredential();
        List<AppRegistration> found = new ArrayList<>();
        ApplicationCollectionResponse page = graph.applications().get(request ->
            request.queryParameters.select = new String[] {"id", "appId", "displayName", "createdDateTime"});
        while (page != null) {
            page.getValue().forEach(application -> found.add(AppRegistration.summary(application.getId(),
                application.getAppId(), application.getDisplayName(), application.getCreatedDateTime())));
            String nextPage = page.getOdataNextLink();
            page = nextPage == null ? null : graph.applications().withUrl(nextPage).get();
        }
        found.sort(comparing(AppRegistration::created, nullsLast(naturalOrder())));
        return found;
    }

    public AppRegistration get(final String clientId) {
        requireCredential();
        return byClientId(clientId).withRoles(grantedRoles(clientId));
    }

    /**
     * Anything that fails once the application exists takes it away again, roles included: a registration
     * whose secret was shown but whose access was never granted is worse than none at all.
     */
    public AppRegistration register(final String applicationName, final List<String> roles) {
        requireCredential();
        Application created = createApplication(applicationName);
        String objectId = created.getId();
        String clientId = created.getAppId();
        try {
            withRetry("create service principal", () -> createServicePrincipal(clientId));
            PasswordCredential secret = withRetry("add password", () -> addPassword(objectId));
            roles.forEach(role -> grantApiAccess(clientId, role));

            log.info("Registered Entra application {} for '{}' with roles {}", clientId, applicationName, roles);
            return AppRegistration.registered(objectId, clientId, secret.getSecretText(),
                String.valueOf(secret.getKeyId()), roles);
        } catch (final RuntimeException registrationFailed) {
            removeOrphan(objectId, registrationFailed);
            throw registrationFailed;
        }
    }

    public void delete(final String clientId) {
        requireCredential();
        deleteByObjectId(byClientId(clientId).objectId());
    }

    private void requireCredential() {
        if (!config.credentialConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "ENTRA_ONBOARDING_CLIENT_SECRET is not configured.");
        }
    }

    private AppRegistration byClientId(final String clientId) {
        return list().stream()
            .filter(application -> clientId.equals(application.clientId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No application with client id " + clientId));
    }

    private Application createApplication(final String applicationName) {
        Application application = new Application();
        application.setDisplayName(applicationName);
        return graph.applications().post(application);
    }

    private ServicePrincipal createServicePrincipal(final String clientId) {
        ServicePrincipal servicePrincipal = new ServicePrincipal();
        servicePrincipal.setAppId(clientId);
        return graph.servicePrincipals().post(servicePrincipal);
    }

    private PasswordCredential addPassword(final String objectId) {
        PasswordCredential wanted = new PasswordCredential();
        wanted.setDisplayName("api-marketplace");
        AddPasswordPostRequestBody request = new AddPasswordPostRequestBody();
        request.setPasswordCredential(wanted);
        return graph.applications().byApplicationId(objectId).addPassword().post(request);
    }

    private void removeOrphan(final String objectId, final RuntimeException cause) {
        try {
            deleteByObjectId(objectId);
            log.warn("Registration failed after the application was created; removed orphaned {}", objectId);
        } catch (final RuntimeException cleanupFailed) {
            cause.addSuppressed(cleanupFailed);
            log.error("Registration failed and orphaned application {} could not be removed.",
                objectId, cleanupFailed);
        }
    }

    /** Takes the object id the caller already holds: a fresh application may not have reached the replica. */
    private void deleteByObjectId(final String objectId) {
        withRetry("delete application", () -> {
            graph.applications().byApplicationId(objectId).delete();
            return null;
        });
        log.info("Deleted Entra application {}", objectId);
    }

    /**
     * Assigns an app role on the marketplace API's service principal. Nothing in APIM changes: its
     * validate-jwt policy already requires the role, and the consumer's token starts carrying it.
     */
    public void grantApiAccess(final String consumerClientId, final String roleValue) {
        requireCredential();
        String consumerPrincipalId = servicePrincipalObjectId(consumerClientId,
            "No service principal for application " + consumerClientId);
        String apiPrincipalId = apiPrincipalId();

        AppRoleAssignment assignment = new AppRoleAssignment();
        assignment.setPrincipalId(UUID.fromString(consumerPrincipalId));
        assignment.setResourceId(UUID.fromString(apiPrincipalId));
        assignment.setAppRoleId(appRoleId(apiPrincipalId, roleValue));
        try {
            graph.servicePrincipals().byServicePrincipalId(apiPrincipalId)
                .appRoleAssignedTo().post(assignment);
        } catch (final ApiException e) {
            throw assignmentRefused(e);
        }
        log.info("Granted {} on {} to application {}", roleValue, config.apiClientId(), consumerClientId);
    }

    private String apiPrincipalId() {
        return servicePrincipalObjectId(config.apiClientId(),
            "No service principal for the API " + config.apiClientId());
    }

    /** Retried: run straight after registering, the service principal is seconds old and may not have replicated. */
    private String servicePrincipalObjectId(final String appId, final String notFound) {
        return withRetry("find service principal " + appId, () -> {
            ServicePrincipalCollectionResponse found = graph.servicePrincipals().get(request ->
                request.queryParameters.filter = "appId eq '" + appId + "'");
            if (found == null || found.getValue() == null || found.getValue().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound);
            }
            return found.getValue().getFirst().getId();
        });
    }

    private UUID appRoleId(final String apiPrincipalId, final String roleValue) {
        List<AppRole> roles = graph.servicePrincipals().byServicePrincipalId(apiPrincipalId).get().getAppRoles();
        return roles.stream()
            .filter(role -> roleValue.equals(role.getValue()))
            .map(AppRole::getId)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The API offers no role '" + roleValue + "'. It has: "
                    + roles.stream().map(AppRole::getValue).toList()));
    }

    private List<String> grantedRoles(final String clientId) {
        String principalId = servicePrincipalObjectId(clientId, "No service principal for " + clientId);
        Map<UUID, String> namesById = rolesOfferedByTheApi();
        return assignmentsFor(principalId).stream()
            .map(assignment -> namesById.get(assignment.getAppRoleId()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    }

    private List<AppRoleAssignment> assignmentsFor(final String principalId) {
        AppRoleAssignmentCollectionResponse response = graph.servicePrincipals()
            .byServicePrincipalId(principalId).appRoleAssignments().get();
        return response == null || response.getValue() == null ? List.of() : response.getValue();
    }

    private Map<UUID, String> rolesOfferedByTheApi() {
        return graph.servicePrincipals().byServicePrincipalId(apiPrincipalId()).get().getAppRoles().stream()
            .collect(Collectors.toMap(AppRole::getId, AppRole::getValue));
    }

    /** 500 rather than 503: only a consent grant fixes this, and a 503 invites a client to retry forever. */
    private ResponseStatusException assignmentRefused(final ApiException cause) {
        log.error("Graph refused the app role assignment with {}: {}",
            cause.getResponseStatusCode(), cause.getMessage(), cause);
        if (cause.getResponseStatusCode() == HttpStatus.FORBIDDEN.value()) {
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Graph refused the app role assignment. The onboarding identity needs the "
                    + "AppRoleAssignment.ReadWrite.All application permission.", cause);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not assign the app role.", cause);
    }

    private <T> T withRetry(final String description, final GraphCall<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                return call.call();
            } catch (final RuntimeException e) {
                lastFailure = e;
                log.warn("Graph call '{}' failed on attempt {}/{}: {}", description, attempt, config.maxAttempts(),
                    e.getMessage());
                if (attempt < config.maxAttempts()) {
                    pause(config.retryDelayMillis());
                }
            }
        }
        log.error("Graph call '{}' did not succeed after {} attempts.", description, config.maxAttempts(), lastFailure);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Could not register the application. Please try again.", lastFailure);
    }

    private void pause(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while retrying.");
        }
    }
}
