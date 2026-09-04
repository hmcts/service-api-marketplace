package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.repository.SubscriptionRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private ClockService clockService;

    @Mock
    private ReferenceGenerator referenceGenerator;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static final Instant SUBMITTED_AT = Instant.parse("2026-01-15T09:00:00Z");

    private SubscriptionRequest subscriptionRequest = SubscriptionRequest.builder().build();
    private OrganisationEntity organisationEntity = OrganisationEntity.builder().name("Org").build();
    private UserEntity userEntity = UserEntity.builder()
        .organisation(organisationEntity)
        .id(1)
        .email("email")
        .firstName("first")
        .lastName("last")
        .build();
    private SubscriptionRequestEntity requestEntity = SubscriptionRequestEntity.builder()
        .orgName(organisationEntity.getName())
        .build();

    @Test
    void submitting_a_request_should_return_response_from_mapper() {
        SubscriptionResponse mappedResponse = SubscriptionResponse.builder().build();
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(subscriptionMapper.toEntity(
            1,
            userEntity.getOrganisation().getName(),
            userEntity.getEmail(),
            userEntity.getFirstName(),
            userEntity.getLastName(),
            subscriptionRequest)).thenReturn(requestEntity);
        when(clockService.now()).thenReturn(SUBMITTED_AT);
        // The service stamps submittedAt via toBuilder, so save() receives a rebuilt instance.
        when(subscriptionRepository.save(any(SubscriptionRequestEntity.class))).thenReturn(requestEntity);
        when(subscriptionMapper.fromEntity(userEntity, requestEntity)).thenReturn(mappedResponse);

        assertThat(subscriptionService.submit(userEntity.getId(), subscriptionRequest)).isEqualTo(mappedResponse);
    }

    @Test
    void submitting_a_request_should_stamp_submitted_at_from_the_clock() {
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(subscriptionMapper.toEntity(
            1,
            userEntity.getOrganisation().getName(),
            userEntity.getEmail(),
            userEntity.getFirstName(),
            userEntity.getLastName(),
            subscriptionRequest)).thenReturn(requestEntity);
        when(clockService.now()).thenReturn(SUBMITTED_AT);
        when(subscriptionRepository.save(any(SubscriptionRequestEntity.class))).thenReturn(requestEntity);

        subscriptionService.submit(userEntity.getId(), subscriptionRequest);

        ArgumentCaptor<SubscriptionRequestEntity> saved = ArgumentCaptor.forClass(SubscriptionRequestEntity.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getSubmittedAt())
            .isEqualTo(LocalDateTime.ofInstant(SUBMITTED_AT, ZoneOffset.UTC));
    }
}
