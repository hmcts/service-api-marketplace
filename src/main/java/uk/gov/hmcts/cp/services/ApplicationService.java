package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registering an application is two real, independent credential grants: an Entra Client
 * ID/Secret (EntraAppRegistrationClient), then one APIM Subscription Key per requested API
 * (ApimSubscriptionClient, via ApimProductRegistry). The two are deliberately independent, matching
 * the prototype's proven model: an API added later would only need a new APIM key, never a new
 * Client ID/Secret.
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
    private final TransactionTemplate transactionTemplate;

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

    /**
     * Order matters, because only the database can be rolled back. Everything that can be checked
     * without side effects is checked first (the user, every short code); then the two external
     * grants are made (Entra application, then one APIM key per API); the database is written last,
     * in one short transaction. If anything fails once the Entra application exists, the external
     * side effects are undone rather than left orphaned - an Entra application whose secret nobody
     * was ever shown, and live APIM keys nobody was given.
     */
    public ApplicationResponse register(final int userId, final ApplicationRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requesting user not found."));

        // Keyed by product so asking for the same API twice issues one key, not two.
        Map<String, String> shortCodeByProductId = new LinkedHashMap<>();
        for (String apiShortCode : request.getApiShortCodes()) {
            shortCodeByProductId.putIfAbsent(apimProductRegistry.productIdFor(apiShortCode), apiShortCode);
        }

        EntraAppRegistrationClient.Registration registration = entraAppRegistrationClient.register(request.getName());
        List<ApimSubscriptionClient.Subscription> issued = new ArrayList<>();
        try {
            for (String productId : shortCodeByProductId.keySet()) {
                issued.add(apimSubscriptionClient.createSubscription(request.getName(), productId));
            }
            return persist(user, request, registration, issued, shortCodeByProductId);
        } catch (RuntimeException failure) {
            rollBackExternal(registration, issued);
            throw failure;
        }
    }

    private ApplicationResponse persist(final UserEntity user, final ApplicationRequest request,
        final EntraAppRegistrationClient.Registration registration,
        final List<ApimSubscriptionClient.Subscription> issued, final Map<String, String> shortCodeByProductId) {
        LocalDateTime now = LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);
        return transactionTemplate.execute(status -> {
            ApplicationEntity application = applicationRepository.save(ApplicationEntity.builder()
                .user(user)
                .name(request.getName())
                .environment(request.getEnvironment())
                .clientId(registration.clientId())
                .entraRegistered(true)
                .createdAt(now)
                .build());

            List<ApplicationApiKeyEntity> apiKeys = issued.stream()
                .map(subscription -> applicationApiKeyRepository.save(ApplicationApiKeyEntity.builder()
                    .application(application)
                    .apiShortCode(shortCodeByProductId.get(subscription.publisherId()))
                    .publisherId(subscription.publisherId())
                    .subscriptionKey(subscription.subscriptionKey())
                    .subscriptionName(subscription.subscriptionName())
                    .createdAt(now)
                    .build()))
                .toList();

            log.info("Registered application '{}' (clientId {}) for userId {} with {} API subscription(s)",
                application.getName(), application.getClientId(), user.getId(), apiKeys.size());
            return applicationMapper.toResponse(application, apiKeys, registration.clientSecret());
        });
    }

    // Best effort and never throws: the caller is already handling the real failure, and masking it
    // with a clean-up failure would hide the cause. Anything that could not be removed is logged
    // with enough to remove it by hand.
    private void rollBackExternal(final EntraAppRegistrationClient.Registration registration,
        final List<ApimSubscriptionClient.Subscription> issued) {
        for (ApimSubscriptionClient.Subscription subscription : issued) {
            try {
                apimSubscriptionClient.deleteSubscription(subscription.subscriptionName());
            } catch (RuntimeException cleanupFailure) {
                log.error("Registration failed and APIM subscription {} could not be deleted - remove it manually",
                    subscription.subscriptionName(), cleanupFailure);
            }
        }
        try {
            entraAppRegistrationClient.delete(registration.clientId());
        } catch (RuntimeException cleanupFailure) {
            log.error("Registration failed and Entra application {} could not be deleted - remove it manually",
                registration.clientId(), cleanupFailure);
        }
    }
}
