package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AppRegistration;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationRepository;

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
    private final ClockService clockService;

    public List<ApplicationResponse> list(final String requestingUserId) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        return applications.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
            .map(ApplicationResponse::from)
            .toList();
    }

    public ApplicationResponse get(final String requestingUserId, final String clientId) {
        return ApplicationResponse.from(requireOwned(users.requireSignedIn(requestingUserId), clientId));
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
            return ApplicationResponse.from(save(user, request, appname, registered))
                .withSecret(registered.clientSecret());
        } catch (final RuntimeException couldNotSave) {
            removeOrphan(registered, couldNotSave);
            throw couldNotSave;
        }
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

    /** 404 rather than 403: a 403 would confirm the application exists and belongs to somebody. */
    private ApplicationEntity requireOwned(final UserEntity user, final String clientId) {
        return applications.findByClientId(clientId)
            .filter(application -> application.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No application with client id " + clientId + "."));
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
