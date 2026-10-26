package uk.gov.hmcts.amp.services;

import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.ApimProperties;
import uk.gov.hmcts.amp.domain.SubscriptionKey;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for API Management when running locally with ENTRA_BYPASS=true. Keys are derived from the
 * subscription name rather than stored, so a key read after a restart matches the one first shown.
 * Ownership is checked exactly as the real service checks it.
 */
@Slf4j
public class LocalApimSubscriptionKeyService extends ApimSubscriptionKeyService {

    private static final String PRODUCTS = "/products/";
    private static final String ACTIVE = "active";

    private final UserService users;
    private final Map<String, SubscriptionKey> subscriptions = new ConcurrentHashMap<>();

    public LocalApimSubscriptionKeyService(final ApiManagementManager apim, final UserService users,
                                           final ApimProperties apimProperties) {
        super(apim, users, apimProperties);
        this.users = users;
    }

    @Override
    public List<SubscriptionKey> list(final String requestingUserId) {
        final String prefix = callerPrefix(requestingUserId);
        return subscriptions.values().stream()
            .filter(subscription -> subscription.name().startsWith(prefix))
            .toList();
    }

    @Override
    public SubscriptionKey create(final String requestingUserId, final String name, final String productId,
                                  final String displayName) {
        final String prefix = callerPrefix(requestingUserId);
        if (!name.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A subscription name must start with " + prefix + ".");
        }
        final SubscriptionKey created = new SubscriptionKey(name, displayName, PRODUCTS + productId, productId,
            ACTIVE, null, null);
        if (subscriptions.putIfAbsent(name, created) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A subscription named " + name + " already exists.");
        }
        log.warn("ENTRA_BYPASS: made up APIM subscription {} for {}", name, productId);
        return created.withKeys(key(name, "primary"), key(name, "secondary"));
    }

    @Override
    public SubscriptionKey keyValues(final String requestingUserId, final String name) {
        requireCallerOwns(requestingUserId, name);
        return SubscriptionKey.keysOnly(name, key(name, "primary"), key(name, "secondary"));
    }

    @Override
    public void delete(final String requestingUserId, final String name) {
        requireCallerOwns(requestingUserId, name);
        subscriptions.remove(name);
        log.warn("ENTRA_BYPASS: forgot APIM subscription {}", name);
    }

    private String callerPrefix(final String requestingUserId) {
        return UserService.namePrefix(users.requireSignedIn(requestingUserId));
    }

    private void requireCallerOwns(final String requestingUserId, final String name) {
        if (!name.startsWith(callerPrefix(requestingUserId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No subscription named " + name + ".");
        }
    }

    private static String key(final String name, final String which) {
        return UUID.nameUUIDFromBytes((which + ":" + name).getBytes(StandardCharsets.UTF_8))
            .toString().replace("-", "");
    }
}
