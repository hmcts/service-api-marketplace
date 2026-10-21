package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.AppRegistration;
import uk.gov.hmcts.amp.domain.ApiSubscriptionResponse;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Applications belong to the person who registered them. Everything here resolves
 * requestingUserId to a user first, and nothing crosses between users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final int HINT_LENGTH = 3;

    private final ApplicationRepository applications;
    private final UserService users;
    private final EntraAppRegistrationService registrations;
    private final ApplicationApiRepository applicationApis;
    private final MarketplaceApiRepository apis;
    private final ApimSubscriptionKeyService subscriptionKeys;
    private final ClockService clockService;
    private final EntraProperties entra;

    public List<ApplicationResponse> list(final String requestingUserId) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        return applications.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
            .map(this::response)
            .toList();
    }

    public ApplicationResponse get(final String requestingUserId, final String clientId) {
        return response(requireOwned(users.requireSignedIn(requestingUserId), clientId));
    }

    /**
     * Entra first, then the row. If the row cannot be written the registration is taken away again,
     * because an application nobody owns is one nobody can find or delete.
     */
    public ApplicationResponse create(final String requestingUserId, final ApplicationRequest request) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        final String appname = request.appname();
        if (applications.existsByUserIdAndAppname(user.getId(), appname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You already have an application called " + request.name() + ".");
        }

        final AppRegistration registered =
            registrations.register(UserService.namePrefix(user) + appname, request.roles());
        try {
            return response(save(user, request, appname, registered))
                .withSecret(registered.clientSecret());
        } catch (final RuntimeException couldNotSave) {
            removeOrphan(registered, couldNotSave);
            throw couldNotSave;
        }
    }

    public ApiSubscriptionResponse subscribe(final String requestingUserId, final String clientId,
                                             final String shortCode) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        final ApplicationEntity application = requireOwned(user, clientId);
        final MarketplaceApiEntity api = requireKnownApi(shortCode);
        requireNotAlreadySubscribed(application, api);

        final String name = UserService.namePrefix(user) + application.getAppname()
            + "-" + api.getShortCode();
        final SubscriptionKey created = subscriptionKeys.create(requestingUserId, name,
            api.getProductId(), application.getName());

        record(application, api, name, created.primaryKey());
        log.info("Subscribed application {} to {} as {}", clientId, api.getShortCode(), name);
        return new ApiSubscriptionResponse(api.getShortCode(), api.getTitle(), api.getProductId(),
            name, created.primaryKey());
    }

    /** The subscription is gone from APIM, so the row claiming the application has it must go too. */
    public void forgetApi(final String subscriptionName) {
        applicationApis.findBySubscriptionName(subscriptionName)
            .ifPresent(applicationApis::delete);
    }

    public void grantRole(final String requestingUserId, final String clientId, final String role) {
        requireOwned(users.requireSignedIn(requestingUserId), clientId);
        registrations.grantApiAccess(clientId, role);
    }

    public void delete(final String requestingUserId, final String clientId) {
        final ApplicationEntity application = requireOwned(users.requireSignedIn(requestingUserId), clientId);
        registrations.delete(clientId);
        applications.delete(application);
        log.info("Deleted application {} for userId {}", clientId, application.getUser().getId());
    }

    private ApplicationEntity save(final UserEntity user, final ApplicationRequest request, final String appname,
                                   final AppRegistration registered) {
        final LocalDateTime now = LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);
        final ApplicationEntity application = new ApplicationEntity();
        application.setUser(user);
        application.setName(request.name().trim());
        application.setAppname(appname);
        application.setClientId(registered.clientId());
        application.setObjectId(registered.objectId());
        application.setSecretHint(hint(registered.clientSecret()));
        application.setSecretKeyId(registered.keyId());
        application.setSecretExpiresAt(expiry(registered, now));
        application.setCreatedAt(now);
        final ApplicationEntity saved = applications.saveAndFlush(application);
        log.info("Registered application {} for userId {}", saved.getClientId(), user.getId());
        return saved;
    }

    private void removeOrphan(final AppRegistration registered, final RuntimeException cause) {
        try {
            registrations.delete(registered.clientId());
            log.warn("Could not record application {}; removed it from Entra", registered.clientId());
        } catch (final RuntimeException cleanupFailed) {
            cause.addSuppressed(cleanupFailed);
            log.error("Could not record application {} and could not remove it either",
                registered.clientId(), cleanupFailed);
        }
    }

    private ApplicationResponse response(final ApplicationEntity application) {
        return ApplicationResponse.from(entra.tenantId(), application);
    }

    /** 404 rather than 403: a 403 would confirm the application exists and belongs to somebody. */
    private ApplicationEntity requireOwned(final UserEntity user, final String clientId) {
        return applications.findByClientId(clientId)
            .filter(application -> application.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No application with client id " + clientId + "."));
    }

    private void record(final ApplicationEntity application, final MarketplaceApiEntity api,
                        final String subscriptionName, final String key) {
        final ApplicationApiEntity row = new ApplicationApiEntity();
        row.setApplication(application);
        row.setShortCode(api.getShortCode());
        row.setProductId(api.getProductId());
        row.setSubscriptionName(subscriptionName);
        row.setKeyHint(hint(key));
        row.setSubscribedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC));
        applicationApis.saveAndFlush(row);
    }

    private MarketplaceApiEntity requireKnownApi(final String shortCode) {
        return apis.findById(shortCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No API with short code " + shortCode + "."));
    }

    private void requireNotAlreadySubscribed(final ApplicationEntity application,
                                             final MarketplaceApiEntity api) {
        if (applicationApis.existsByApplicationIdAndShortCode(application.getId(), api.getShortCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                application.getName() + " is already subscribed to " + api.getShortCode() + ".");
        }
    }

    private static String hint(final String secret) {
        return secret == null || secret.length() < HINT_LENGTH ? "" : secret.substring(0, HINT_LENGTH);
    }

    private static LocalDateTime expiry(final AppRegistration registered, final LocalDateTime fallback) {
        return registered.secretExpiresAt() == null
            ? fallback.plusYears(2)
            : registered.secretExpiresAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
