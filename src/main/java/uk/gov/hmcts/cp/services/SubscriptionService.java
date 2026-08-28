package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.MarketplaceRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mapper.SubscriptionMapper;
import uk.gov.hmcts.cp.repository.MarketplaceRequestRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final MarketplaceRequestRepository marketplaceRequestRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClockService clockService;

    public SubscriptionResponse submit(final UserEntity user, final SubscriptionRequest request) {
        MarketplaceRequestEntity entity = subscriptionMapper.toEntity(request, user)
            .toBuilder()
            .id(UUID.randomUUID())
            .submittedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC))
            .build();

        marketplaceRequestRepository.save(entity);
        log.info("Subscription request {} submitted by {} {}", entity.getId(), user.getFirstName(), user.getLastName());
        return subscriptionMapper.fromEntity(entity, request);
    }
}
