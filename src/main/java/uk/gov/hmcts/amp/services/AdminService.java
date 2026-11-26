package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AdminApplicationDetail;
import uk.gov.hmcts.amp.domain.AdminApplicationSummary;
import uk.gov.hmcts.amp.domain.AdminUserDetail;
import uk.gov.hmcts.amp.domain.AdminUserSummary;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository.ApplicationApiCount;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository.UserApplicationCount;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationApiRepository applicationApiRepository;
    private final ProductionRequestRepository productionRequestRepository;
    private final MarketplaceApiRepository marketplaceApiRepository;

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

    public List<AdminApplicationSummary> listApplications() {
        final Map<Long, Long> apis = applicationApiRepository.countByApplication().stream()
            .collect(Collectors.toMap(ApplicationApiCount::getApplicationId, ApplicationApiCount::getTotal));
        final Set<Long> requested = productionRequestRepository.findRequestedApplicationIds();
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(application -> new AdminApplicationSummary(application.getId(), application.getName(),
                ownerOf(application), apis.getOrDefault(application.getId(), 0L), application.getCreatedAt(),
                requested.contains(application.getId())))
            .toList();
    }

    public AdminApplicationDetail getApplication(final long applicationId) {
        final ApplicationEntity application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No application " + applicationId + "."));
        final List<ApplicationApiEntity> subscriptions =
            applicationApiRepository.findByApplicationIdOrderBySubscribedAtAsc(applicationId);
        final Map<String, String> titles = marketplaceApiRepository
            .findAllById(subscriptions.stream().map(ApplicationApiEntity::getShortCode).distinct().toList())
            .stream()
            .collect(Collectors.toMap(MarketplaceApiEntity::getShortCode, MarketplaceApiEntity::getTitle));
        final AdminUserDetail.ProductionRequest latest =
            productionRequestRepository.findByApplicationIdOrderBySubmittedAtDesc(applicationId).stream()
                .findFirst()
                .map(request -> new AdminUserDetail.ProductionRequest(request.getReference(),
                    request.getApplicationName(), request.getStatus(), request.getSubmittedAt()))
                .orElse(null);
        return new AdminApplicationDetail(application.getId(), application.getName(), application.getClientId(),
            application.getCreatedAt(), ownerOf(application),
            subscriptions.stream().map(api -> titles.getOrDefault(api.getShortCode(), api.getShortCode())).toList(),
            latest);
    }

    private static AdminApplicationSummary.Owner ownerOf(final ApplicationEntity application) {
        final UserEntity user = application.getUser();
        return user == null ? null : new AdminApplicationSummary.Owner(user.getId(), nameOf(user), user.getEmail());
    }

    private static String nameOf(final UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
