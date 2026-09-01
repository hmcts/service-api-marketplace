package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.MarketplaceRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.repository.MarketplaceRequestRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final MarketplaceRequestRepository marketplaceRequestRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClockService clockService;

    public List<SubscriptionResponse> getAll() {
        return marketplaceRequestRepository.findAll().stream()
            .flatMap(entity -> userRepository.findByEmail(entity.getUserEmail()).stream()
                .map(user -> subscriptionMapper.fromEntity(entity, user)))
            .toList();
    }

    public void delete(final UUID id) {
        if (!marketplaceRequestRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.");
        }
        marketplaceRequestRepository.deleteById(id);
        log.info("Subscription request {} deleted", id);
    }

    public SubscriptionResponse submit(final UserEntity user, final SubscriptionRequest request) {
        MarketplaceRequestEntity entity = subscriptionMapper.toEntity(request, user)
            .toBuilder()
            .id(UUID.randomUUID())
            .submittedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC))
            .build();

        marketplaceRequestRepository.save(entity);
        log.info("Subscription request {} submitted by userId:{}", entity.getId(), user.getId());
        return subscriptionMapper.fromEntity(entity, user);
    }
}
