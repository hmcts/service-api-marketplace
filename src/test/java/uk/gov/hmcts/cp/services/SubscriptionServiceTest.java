package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.MarketplaceRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.repository.MarketplaceRequestRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private MarketplaceRequestRepository marketplaceRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private ClockService clockService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static final SubscriptionRequest REQUEST = SubscriptionRequest.builder().build();
    private static final UserEntity USER = UserEntity.builder().build();

    @Test
    void submitting_a_request_should_save_entity_with_id_and_submitted_at() {
        Instant now = Instant.parse("2026-08-28T10:00:00Z");
        when(subscriptionMapper.toEntity(REQUEST, USER)).thenReturn(MarketplaceRequestEntity.builder().build());
        when(subscriptionMapper.fromEntity(any(MarketplaceRequestEntity.class), any(UserEntity.class)))
            .thenReturn(SubscriptionResponse.builder().build());
        when(clockService.now()).thenReturn(now);

        subscriptionService.submit(USER, REQUEST);

        ArgumentCaptor<MarketplaceRequestEntity> captor = ArgumentCaptor.forClass(MarketplaceRequestEntity.class);
        verify(marketplaceRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getSubmittedAt()).isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Test
    void submitting_a_request_should_return_response_from_mapper() {
        SubscriptionResponse mappedResponse = SubscriptionResponse.builder().build();
        when(subscriptionMapper.toEntity(REQUEST, USER)).thenReturn(MarketplaceRequestEntity.builder().build());
        when(subscriptionMapper.fromEntity(any(MarketplaceRequestEntity.class), any(UserEntity.class)))
            .thenReturn(mappedResponse);
        when(clockService.now()).thenReturn(Instant.parse("2026-08-28T10:00:00Z"));

        assertThat(subscriptionService.submit(USER, REQUEST)).isEqualTo(mappedResponse);
    }
}
