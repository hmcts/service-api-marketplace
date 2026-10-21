package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.domain.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.domain.ProductionAccessResponse;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
    private final UserService users;
    private final NotifyService notify;
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

        sendEmailToRequestor(user, saved);
        return ProductionAccessResponse.from(saved);
    }

    /**
     * Sent after the row is written and its failure swallowed: the request is recorded either way,
     * and failing the call would invite a retry that submits it twice. Synchronous for now - a
     * queue is what makes a failed send recoverable rather than only logged.
     */
    private void sendEmailToRequestor(final UserEntity user, final ProductionRequestEntity saved) {
        try {
            notify.send(new ProductionAccessRequestEmail(user.getEmail(), displayName(user),
                saved.getApplicationName(), List.of(), saved.getReference()));
        } catch (final RuntimeException notSent) {
            log.error("Production request {} was recorded but its email was not sent",
                saved.getReference(), notSent);
        }
    }

    private static String displayName(final UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
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
