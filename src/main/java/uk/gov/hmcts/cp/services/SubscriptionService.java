package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.RequestType;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.repository.SubscriptionRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClockService clockService;
    private final ReferenceGenerator referenceGenerator;

    public List<SubscriptionResponse> getAll() {
        return subscriptionRepository.findAll().stream()
            .flatMap(entity -> userRepository.findByEmail(entity.getUserEmail()).stream()
                .map(userEntity -> subscriptionMapper.fromEntity(userEntity, entity)))
            .toList();
    }

    public void delete(final UUID id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.");
        }
        subscriptionRepository.deleteById(id);
        log.info("Subscription request {} deleted", id);
    }

    public SubscriptionResponse submit(final int userId, final SubscriptionRequest request) {
        log.info("Subscription request submitted by userId:{}", userId);
        UserEntity userEntity = userRepository.findById(userId).orElseThrow();
        SubscriptionRequestEntity entity = subscriptionMapper.toEntity(
                userId,
                userEntity.getOrganisation().getName(),
                userEntity.getEmail(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                request
            )
            .toBuilder()
            .reference(referenceGenerator.generate(RequestType.SUBSCRIPTION))
            // Stamped from ClockService rather than the entity so tests can fix the time.
            .submittedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC))
            .build();
        SubscriptionRequestEntity saved = subscriptionRepository.save(entity);
        return subscriptionMapper.fromEntity(userEntity, saved);
    }
}
