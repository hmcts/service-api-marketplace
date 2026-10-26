package uk.gov.hmcts.amp.services;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.ApiGrant;
import uk.gov.hmcts.amp.domain.AppRegistration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Comparator.comparing;

/**
 * Stands in for Entra when running locally with ENTRA_BYPASS=true. Registrations live in memory, but
 * the rows that point at them survive a restart in Postgres, so delete and grant never fail on a
 * client id this instance has not seen.
 */
@Slf4j
public class LocalEntraAppRegistrationService extends EntraAppRegistrationService {

    private static final int SECRET_YEARS = 2;

    private final Map<String, AppRegistration> registrations = new ConcurrentHashMap<>();
    private final Map<String, List<String>> roles = new ConcurrentHashMap<>();

    public LocalEntraAppRegistrationService(final GraphServiceClient graph, final EntraProperties config) {
        super(graph, config);
    }

    @Override
    public List<AppRegistration> list() {
        List<AppRegistration> found = new ArrayList<>(registrations.values());
        found.sort(comparing(AppRegistration::created));
        return found;
    }

    @Override
    public AppRegistration get(final String clientId) {
        AppRegistration registration = registrations.get(clientId);
        if (registration == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No application with client id " + clientId);
        }
        return registration.withRoles(List.copyOf(roles.getOrDefault(clientId, List.of())));
    }

    @Override
    public AppRegistration register(final String applicationName, final List<ApiGrant> grants) {
        String objectId = UUID.randomUUID().toString();
        String clientId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        registrations.put(clientId, AppRegistration.summary(objectId, clientId, applicationName, now));
        roles.put(clientId, new ArrayList<>(grants.stream().map(ApiGrant::role).toList()));
        List<String> apis = grants.stream().map(ApiGrant::shortCode).toList();
        log.warn("ENTRA_BYPASS: made up registration {} for '{}' with access to {}", clientId, applicationName, apis);
        return AppRegistration.registered(objectId, clientId, "local~" + UUID.randomUUID(),
            UUID.randomUUID().toString(), apis, now.plusYears(SECRET_YEARS));
    }

    @Override
    public void delete(final String clientId) {
        registrations.remove(clientId);
        roles.remove(clientId);
        log.warn("ENTRA_BYPASS: forgot registration {}", clientId);
    }

    @Override
    public void grantApiAccess(final String consumerClientId, final String resourceAppId,
                               final String roleValue) {
        roles.computeIfAbsent(consumerClientId, unknown -> new ArrayList<>()).add(roleValue);
        log.warn("ENTRA_BYPASS: granted {} on {} to {}", roleValue, resourceAppId, consumerClientId);
    }
}
