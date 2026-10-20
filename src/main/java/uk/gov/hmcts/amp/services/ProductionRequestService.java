package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.config.NotifyProperties;
import uk.gov.hmcts.amp.mappers.NotifyMapper;
import uk.gov.hmcts.amp.domain.ProductionAccessResponse;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Requests for production credentials. Nothing here issues anything: client ids, secrets and
 * subscription keys are still created by hand offline, and this records what was asked for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestService {

    private static final String PREFIX = "PCR-";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_LENGTH = 8;
    private static final int REFERENCE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductionRequestRepository requests;
    private final ApplicationRepository applications;
    private final ApplicationApiRepository applicationApis;
    private final MarketplaceApiRepository apis;
    private final UserService users;
    private final NotifyService notify;
    private final NotifyProperties notifyProperties;
    private final NotifyMapper notifyMapper;
    private final ClockService clockService;

    public List<ProductionAccessResponse> list(final String requestingUserId) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        return requests.findByUserIdOrderBySubmittedAtDesc(user.getId()).stream()
            .map(ProductionAccessResponse::from)
            .toList();
    }

    public ProductionAccessDetail get(final String requestingUserId, final String reference) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        return requests.findByUserIdAndReference(user.getId(), reference)
            .map(ProductionAccessDetail::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No request with reference " + reference + "."));
    }

    public ProductionAccessResponse create(final String requestingUserId,
                                           final ProductionAccessRequest request) {
        final UserEntity user = users.requireSignedIn(requestingUserId);
        final ApplicationEntity application = requireOwned(user, request.clientId());
        final ProductionRequestEntity saved = requests.saveAndFlush(record(user, application, request));
        log.info("Production request {} for application {} by userId {}",
            saved.getReference(), application.getClientId(), user.getId());

        final List<String> subscribed = subscribedApis(application);
        sendEmailToRequestor(user, saved, subscribed);
        sendEmailToReviewers(user, saved, subscribed);
        return ProductionAccessResponse.from(saved);
    }

    /**
     * Sent after the row is written and its failure swallowed: the request is recorded either way,
     * and failing the call would invite a retry that submits it twice. Synchronous for now - a
     * queue is what makes a failed send recoverable rather than only logged.
     */
    private void sendEmailToRequestor(final UserEntity user, final ProductionRequestEntity saved,
                                      final List<String> subscribed) {
        try {
            notify.send(notifyMapper.toRequesterEmail(saved, user, subscribed));
        } catch (final RuntimeException notSent) {
            log.error("Production request {} was recorded but its email was not sent",
                saved.getReference(), notSent);
        }
    }

    private List<String> subscribedApis(final ApplicationEntity application) {
        final Map<String, String> titles = apis.findAll().stream()
            .collect(Collectors.toMap(MarketplaceApiEntity::getShortCode, MarketplaceApiEntity::getTitle));
        return applicationApis.findByApplicationId(application.getId()).stream()
            .map(subscribed -> titles.getOrDefault(subscribed.getShortCode(), subscribed.getShortCode()))
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * The panel, the asset owner and the marketplace team share one inbox and discuss the request
     * on the thread, so this is one email rather than three. The requester is not copied.
     */
    private void sendEmailToReviewers(final UserEntity user, final ProductionRequestEntity saved,
                                      final List<String> subscribed) {
        if (!notifyProperties.hasReviewInbox()) {
            log.info("Production request {} was recorded; no review inbox is configured",
                saved.getReference());
            return;
        }
        try {
            notify.send(notifyMapper.toReviewEmail(saved, user, subscribed,
                notifyProperties.reviewInbox()));
        } catch (final RuntimeException notSent) {
            log.error("Production request {} was recorded but the review email was not sent",
                saved.getReference(), notSent);
        }
    }

    private ProductionRequestEntity record(final UserEntity user, final ApplicationEntity application,
                                           final ProductionAccessRequest request) {
        final ProductionRequestEntity entity = new ProductionRequestEntity();
        entity.setUser(user);
        entity.setApplication(application);
        entity.setApplicationName(application.getName());
        entity.setReference(newReference());
        entity.setStatus(ProductionRequestEntity.SUBMITTED);
        entity.setSubmittedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC));
        entity.setOrganisation(request.organisation().trim());
        entity.setGoLiveDate(request.goLiveDate());
        entity.setCallVolume(request.callVolume());
        entity.setDataRequest(request.dataRequest().trim());
        entity.setProposedOutputs(request.proposedOutputs().trim());
        return entity;
    }

    private ApplicationEntity requireOwned(final UserEntity user, final String clientId) {
        return applications.findByClientId(clientId)
            .filter(application -> application.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No application with client id " + clientId + "."));
    }

    private String newReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            final String reference = PREFIX + randomSuffix();
            if (!requests.existsByReference(reference)) {
                return reference;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Could not allocate a reference.");
    }

    private static String randomSuffix() {
        final StringBuilder suffix = new StringBuilder(REFERENCE_LENGTH);
        for (int character = 0; character < REFERENCE_LENGTH; character++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}
