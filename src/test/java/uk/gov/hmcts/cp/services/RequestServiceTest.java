package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import uk.gov.hmcts.cp.domain.RequestSummaryResponse;
import uk.gov.hmcts.cp.domain.RequestType;
import uk.gov.hmcts.cp.entity.PublishRequestEntity;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.repository.PublishRepository;
import uk.gov.hmcts.cp.repository.SubscriptionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    private static final String EMAIL = "joe.bloggs@example.com";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PublishRepository publishRepository;

    @InjectMocks
    private RequestService requestService;

    private SubscriptionRequestEntity subscription(final LocalDateTime at) {
        return SubscriptionRequestEntity.builder()
            .reference("AR-2026-ABC123").status("NEW").submittedAt(at).build();
    }

    private PublishRequestEntity publication(final LocalDateTime at) {
        return PublishRequestEntity.builder()
            .reference("PR-2026-ABC123").status("NEW").submittedAt(at).build();
    }

    @Test
    void both_kinds_should_appear_in_one_list() {
        when(subscriptionRepository.findByUserEmail(EMAIL)).thenReturn(List.of(subscription(LocalDateTime.now())));
        when(publishRepository.findByUserEmail(EMAIL)).thenReturn(List.of(publication(LocalDateTime.now())));

        List<RequestSummaryResponse> list = requestService.getForUser(EMAIL);

        assertThat(list).extracting(RequestSummaryResponse::getType)
            .containsExactlyInAnyOrder(RequestType.SUBSCRIPTION, RequestType.PUBLISH);
    }

    @Test
    void the_list_should_be_newest_first_across_both_kinds() {
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime middle = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDateTime recent = LocalDateTime.of(2026, 9, 1, 9, 0);

        when(subscriptionRepository.findByUserEmail(EMAIL))
            .thenReturn(List.of(subscription(old), subscription(recent)));
        when(publishRepository.findByUserEmail(EMAIL)).thenReturn(List.of(publication(middle)));

        List<RequestSummaryResponse> list = requestService.getForUser(EMAIL);

        assertThat(list).extracting(RequestSummaryResponse::getSubmittedAt)
            .containsExactly(recent, middle, old);
    }

    @Test
    void each_summary_should_carry_the_requests_own_reference() {
        SubscriptionRequestEntity entity = subscription(LocalDateTime.now());
        when(subscriptionRepository.findByUserEmail(EMAIL)).thenReturn(List.of(entity));
        when(publishRepository.findByUserEmail(EMAIL)).thenReturn(List.of());

        assertThat(requestService.getForUser(EMAIL).get(0).getReference()).isEqualTo(entity.getReference());
    }

    @Test
    void having_submitted_nothing_should_give_an_empty_list_not_everyone_elses() {
        when(subscriptionRepository.findByUserEmail(EMAIL)).thenReturn(List.of());
        when(publishRepository.findByUserEmail(EMAIL)).thenReturn(List.of());

        assertThat(requestService.getForUser(EMAIL)).isEmpty();
    }

    @Test
    void both_tables_should_be_queried_by_the_requesters_address() {
        when(subscriptionRepository.findByUserEmail(EMAIL)).thenReturn(List.of());
        when(publishRepository.findByUserEmail(EMAIL)).thenReturn(List.of());

        requestService.getForUser(EMAIL);

        verify(subscriptionRepository).findByUserEmail(EMAIL);
        verify(publishRepository).findByUserEmail(EMAIL);
    }
}
