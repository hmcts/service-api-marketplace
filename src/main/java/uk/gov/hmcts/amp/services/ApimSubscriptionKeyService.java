package uk.gov.hmcts.amp.services;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.azure.resourcemanager.apimanagement.models.SubscriptionContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionCreateParameters;
import com.azure.resourcemanager.apimanagement.models.SubscriptionKeysContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.ApimProperties;
import uk.gov.hmcts.amp.domain.ConsumerNames;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApimSubscriptionKeyService {

    private static final String MATCH_ANY_STATE = "*";
    private static final String PRODUCTS = "/products/";

    private final ApiManagementManager apim;
    private final UserService users;
    private final ApplicationRepository applications;
    private final ApplicationApiRepository applicationApis;
    private final ApimProperties apimProperties;

    public List<SubscriptionKey> list(final String requestingUserId) {
        final Set<String> owned = ownedNames(requestingUserId);
        // The listing is paged lazily, so Azure is only called, and can only fail, inside the stream
        try {
            return apim.subscriptions().list(apimProperties.resourceGroup(), apimProperties.serviceName()).stream()
                .map(ApimSubscriptionKeyService::asSubscriptionKey)
                .filter(subscription -> owned.contains(subscription.name()))
                .toList();
        } catch (final ManagementException e) {
            throw translate("*", e);
        }
    }

    public SubscriptionKey create(final String requestingUserId, final String name, final String productId,
                                  final String displayName) {
        requireBuiltFromOwnApplication(requestingUserId, name);
        // Azure's createOrUpdate is a PUT, so without this a second create silently re-points an
        // existing key at a different product and reports it as created.
        if (exists(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A subscription named " + name + " already exists.");
        }
        try {
            SubscriptionContract created = apim.subscriptions().createOrUpdate(
                apimProperties.resourceGroup(), apimProperties.serviceName(), name,
                new SubscriptionCreateParameters()
                    .withScope(PRODUCTS + productId)
                    .withDisplayName(displayName)
                    .withState(SubscriptionState.ACTIVE));
            log.info("Created APIM subscription {}", name);
            return withKeys(asSubscriptionKey(created));
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    public SubscriptionKey keyValues(final String requestingUserId, final String name) {
        requireOwned(requestingUserId, name);
        try {
            SubscriptionKeysContract secrets = apim.subscriptions()
                .listSecrets(apimProperties.resourceGroup(), apimProperties.serviceName(), name);
            return SubscriptionKey.keysOnly(name, secrets.primaryKey(), secrets.secondaryKey());
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    public void delete(final String requestingUserId, final String name) {
        requireOwned(requestingUserId, name);
        // Azure answers a delete of something absent with success, which on a shared instance
        // leaves you unable to tell a revoked key from one that was never there.
        if (!exists(name)) {
            throw noSuchSubscription(name);
        }
        try {
            apim.subscriptions().delete(apimProperties.resourceGroup(),
                apimProperties.serviceName(), name, MATCH_ANY_STATE);
            log.info("Deleted APIM subscription {}", name);
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    /** Only for a subscription this service has just created and could not record, so it has no owner yet. */
    public void discard(final String name) {
        try {
            apim.subscriptions().delete(apimProperties.resourceGroup(),
                apimProperties.serviceName(), name, MATCH_ANY_STATE);
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    // API short codes contain no hyphen, so a name cannot match another application's prefix.
    protected void requireBuiltFromOwnApplication(final String requestingUserId, final String name) {
        final Long userId = users.requireSignedIn(requestingUserId).getId();
        final boolean own = applications.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(application -> ConsumerNames.subscription(application.getAppname(), ""))
            .anyMatch(prefix -> name.length() > prefix.length() && name.startsWith(prefix)
                && name.indexOf('-', prefix.length()) < 0);
        if (!own) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A subscription name must be built from one of your applications.");
        }
    }

    protected Set<String> ownedNames(final String requestingUserId) {
        final Long userId = users.requireSignedIn(requestingUserId).getId();
        return applicationApis.findByApplicationUserId(userId).stream()
            .map(ApplicationApiEntity::getSubscriptionName)
            .collect(Collectors.toSet());
    }

    /** 404 rather than 403, so a name outside the caller's own reads the same as one that does not exist. */
    protected void requireOwned(final String requestingUserId, final String name) {
        if (!ownedNames(requestingUserId).contains(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No subscription named " + name + ".");
        }
    }

    private boolean exists(final String name) {
        try {
            apim.subscriptions().get(apimProperties.resourceGroup(), apimProperties.serviceName(), name);
            return true;
        } catch (final ManagementException e) {
            if (e.getResponse() != null && e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw translate(name, e);
        }
    }

    private SubscriptionKey withKeys(final SubscriptionKey subscription) {
        SubscriptionKeysContract secrets = apim.subscriptions()
            .listSecrets(apimProperties.resourceGroup(), apimProperties.serviceName(), subscription.name());
        return subscription.withKeys(secrets.primaryKey(), secrets.secondaryKey());
    }

    private static SubscriptionKey asSubscriptionKey(final SubscriptionContract subscription) {
        return new SubscriptionKey(
            subscription.name(),
            subscription.displayName(),
            subscription.scope(),
            productIdIn(subscription.scope()),
            subscription.state() == null ? null : subscription.state().toString(),
            null, null);
    }

    // Azure returns the scope as a full resource path. Null for a subscription scoped to the whole
    // service or to all APIs rather than to one product.
    private static String productIdIn(final String scope) {
        if (scope == null) {
            return null;
        }
        int marker = scope.lastIndexOf(PRODUCTS);
        return marker < 0 ? null : scope.substring(marker + PRODUCTS.length());
    }

    // Azure's message names the resource group and instance, which the caller has no business seeing.
    /** A missing instance answers 404 too, so the message has to say where it looked. */
    private ResponseStatusException noSuchSubscription(final String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No subscription named " + name
            + " on " + apimProperties.serviceName() + " in " + apimProperties.resourceGroup());
    }

    private RuntimeException translate(final String name, final ManagementException cause) {
        if (cause.getResponse() != null && cause.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
            return noSuchSubscription(name);
        }
        if (cause.getResponse() != null && cause.getResponse().getStatusCode() == HttpStatus.FORBIDDEN.value()) {
            // API Management answered, and said no. Nothing the caller can do and nothing to retry:
            // the service identity has no role on the instance.
            log.error("API Management refused the service identity. It needs a role on the instance.", cause);
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Not permitted to manage subscriptions on API Management.");
        }
        log.error("APIM call for subscription {} failed.", name, cause);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Could not reach API Management. Please try again.");
    }
}
