package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AdminUserDetail;
import uk.gov.hmcts.amp.domain.AdminUserSummary;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository.UserApplicationCount;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationApiRepository applicationApiRepository;
    private final ProductionRequestRepository productionRequestRepository;

    public AdminUserDetail getUser(final long userId) {
        final UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user " + userId + "."));
        final List<AdminUserDetail.Application> applications =
            applicationRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(application -> new AdminUserDetail.Application(application.getName(),
                    applicationApiRepository.countByApplicationId(application.getId()),
                    application.getCreatedAt()))
                .toList();
        final List<ProductionRequestEntity> requests =
            productionRequestRepository.findByUserIdOrderBySubmittedAtDesc(user.getId());
        return new AdminUserDetail(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim(),
            user.getEmail(), user.getStatus(), user.getCreatedAt(), user.getLastLoginAt(),
            requests.isEmpty() ? null : requests.get(0).getOrganisation(), applications,
            requests.stream().map(request -> new AdminUserDetail.ProductionRequest(request.getReference(),
                request.getApplicationName(), request.getStatus(), request.getSubmittedAt())).toList());
    }

    public List<AdminUserSummary> listUsers() {
        final Map<Long, Long> applications = applicationRepository.countByUser().stream()
            .collect(Collectors.toMap(UserApplicationCount::getUserId, UserApplicationCount::getTotal));
        return userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE).stream()
            .map(user -> AdminUserSummary.from(user, applications.getOrDefault(user.getId(), 0L)))
            .toList();
    }
}