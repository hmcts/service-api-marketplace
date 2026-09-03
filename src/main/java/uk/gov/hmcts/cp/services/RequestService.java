package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.RequestSummaryResponse;
import uk.gov.hmcts.cp.domain.RequestType;
import uk.gov.hmcts.cp.entity.PublishRequestEntity;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.repository.PublishRepository;
import uk.gov.hmcts.cp.repository.SubscriptionRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private final SubscriptionRepository subscriptionRepository;
    private final PublishRepository publishRepository;

    public List<RequestSummaryResponse> getForUser(final String userEmail) {
        List<RequestSummaryResponse> summaries = Stream.concat(
                subscriptionRepository.findByUserEmail(userEmail).stream().map(this::toSummary),
                publishRepository.findByUserEmail(userEmail).stream().map(this::toSummary)
            )
            .sorted(Comparator.comparing(RequestSummaryResponse::getSubmittedAt).reversed())
            .toList();

        log.info("Listed {} requests for a user", summaries.size());
        return summaries;
    }

    private RequestSummaryResponse toSummary(final SubscriptionRequestEntity entity) {
        return RequestSummaryResponse.builder()
            .reference(entity.getId())
            .type(RequestType.SUBSCRIPTION)
            .submittedAt(entity.getSubmittedAt())
            .status(entity.getStatus())
            .build();
    }

    private RequestSummaryResponse toSummary(final PublishRequestEntity entity) {
        return RequestSummaryResponse.builder()
            .reference(entity.getId())
            .type(RequestType.PUBLISH)
            .submittedAt(entity.getSubmittedAt())
            .status(entity.getStatus())
            .build();
    }
}
