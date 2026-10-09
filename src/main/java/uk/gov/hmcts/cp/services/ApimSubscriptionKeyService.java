package uk.gov.hmcts.cp.services;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.azure.resourcemanager.apimanagement.models.ProductContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionCreateParameters;
import com.azure.resourcemanager.apimanagement.models.SubscriptionKeysContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApimProduct;
import uk.gov.hmcts.cp.domain.SubscriptionKey;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApimSubscriptionKeyService {

    private static final String MATCH_ANY_STATE = "*";
    private static final String PRODUCTS = "/products/";

    private final ApiManagementManager apim;

    @Value("${apim.resource-group}")
    private String resourceGroup;

    @Value("${apim.service-name}")
    private String serviceName;

    public List<SubscriptionKey> list() {
        return apim.subscriptions().list(resourceGroup, serviceName).stream()
            .map(ApimSubscriptionKeyService::asSubscriptionKey)
            .toList();
    }

    public List<ApimProduct> listProducts() {
        return apim.products().listByService(resourceGroup, serviceName).stream()
            .map(ApimSubscriptionKeyService::asProduct)
            .toList();
    }

    public SubscriptionKey create(final String name, final String productId, final String displayName) {
        // Azure's createOrUpdate is a PUT, so without this a second create silently re-points an
        // existing key at a different product and reports it as created.
        if (exists(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A subscription named " + name + " already exists.");
        }
        try {
            SubscriptionContract created = apim.subscriptions().createOrUpdate(
                resourceGroup, serviceName, name,
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

    public SubscriptionKey keyValues(final String name) {
        try {
            SubscriptionKeysContract secrets = apim.subscriptions()
                .listSecrets(resourceGroup, serviceName, name);
            return SubscriptionKey.keysOnly(name, secrets.primaryKey(), secrets.secondaryKey());
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    public void delete(final String name) {
        // Azure answers a delete of something absent with success, which on a shared instance
        // leaves you unable to tell a revoked key from one that was never there.
        if (!exists(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No subscription named " + name + " on " + serviceName + " in " + resourceGroup);
        }
        try {
            apim.subscriptions().delete(resourceGroup, serviceName, name, MATCH_ANY_STATE);
            log.info("Deleted APIM subscription {}", name);
        } catch (final ManagementException e) {
            throw translate(name, e);
        }
    }

    private boolean exists(final String name) {
        try {
            apim.subscriptions().get(resourceGroup, serviceName, name);
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
            .listSecrets(resourceGroup, serviceName, subscription.name());
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

    private static ApimProduct asProduct(final ProductContract product) {
        return new ApimProduct(
            product.name(),
            product.displayName(),
            product.description(),
            product.state() == null ? null : product.state().toString());
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
    private RuntimeException translate(final String name, final ManagementException cause) {
        if (cause.getResponse() != null && cause.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No subscription named " + name + " on " + serviceName + " in " + resourceGroup);
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
