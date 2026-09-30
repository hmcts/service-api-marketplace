package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApplicationRequest;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.ApplicationMapper;
import uk.gov.hmcts.cp.repository.ApplicationApiKeyRepository;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Registering an application is two real, independent credential grants: an Entra Client
 * ID/Secret (EntraAppRegistrationClient), then one APIM Subscription Key per requested API
 * (ApimSubscriptionClient, via ApimProductRegistry). Adding an API to an application already
 * registered never needs to touch Entra again - only subscribe() is called - matching the
 * prototype's proven model that the two credentials are deliberately independent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationApiKeyRepository applicationApiKeyRepository;
    private final UserRepository userRepository;
    private final ApplicationMapper applicationMapper;
    private final ClockService clockService;
    private final EntraAppRegistrationClient entraAppRegistrationClient;
    private final ApimSubscriptionClient apimSubscriptionClient;
    private final ApimProductRegistry apimProductRegistry;

    public List<ApplicationResponse> getForUser(final int userId) {
        return applicationRepository.findByUserId(userId).stream()
            // No clientSecret here: Entra only ever returns it once, on the response that
            // created the application, and it is never persisted.
            .map(application -> applicationMapper.toResponse(
                application,
                applicationApiKeyRepository.findByApplicationId(application.getId()),
                null))
            .toList();
    }

    public ApplicationResponse register(final int userId, final ApplicationRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requesting user not found."));

        EntraAppRegistrationClient.Registration registration = entraAppRegistrationClient.register(request.getName());
        LocalDateTime now = LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);

        ApplicationEntity application = applicationRepository.save(ApplicationEntity.builder()
            .user(user)
            .name(request.getName())
            .environment(request.getEnvironment())
            .clientId(registration.clientId())
            .createdAt(now)
            .build());

        List<ApplicationApiKeyEntity> apiKeys = request.getApiShortCodes().stream()
            .map(apiShortCode -> subscribe(application, apiShortCode, now))
            .toList();

        log.info("Registered application '{}' (clientId {}) for userId {} with {} API subscription(s)",
            application.getName(), application.getClientId(), userId, apiKeys.size());

        return applicationMapper.toResponse(application, apiKeys, registration.clientSecret());
    }

    private ApplicationApiKeyEntity subscribe(final ApplicationEntity application, final String apiShortCode,
        final LocalDateTime now) {
        String productId = apimProductRegistry.productIdFor(apiShortCode);
        ApimSubscriptionClient.Subscription subscription =
            apimSubscriptionClient.createSubscription(application.getName(), productId);
        return applicationApiKeyRepository.save(ApplicationApiKeyEntity.builder()
            .application(application)
            .apiShortCode(apiShortCode)
            .publisherId(subscription.publisherId())
            .subscriptionKey(subscription.subscriptionKey())
            .createdAt(now)
            .build());
    }
}
