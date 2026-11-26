package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationApiRepository applicationApiRepository;

    @Mock
    private ProductionRequestRepository productionRequestRepository;

    @Mock
    private MarketplaceApiRepository marketplaceApiRepository;

    @InjectMocks
    private AdminService service;

    @Test
    void organisation_should_come_from_the_latest_production_request() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "Grace", "Hopper")));
        when(applicationRepository.findByUserIdOrderByCreatedAtAsc(2L)).thenReturn(List.of());
        when(productionRequestRepository.findByUserIdOrderBySubmittedAtDesc(2L))
            .thenReturn(List.of(request("PCR-NEWER001", "Example Legal Ltd"), request("PCR-OLDER001", "Old Org")));

        final AdminUserDetail user = service.getUser(2L);

        assertThat(user.organisation()).isEqualTo("Example Legal Ltd");
        assertThat(user.productionRequests()).extracting(AdminUserDetail.ProductionRequest::reference)
            .containsExactly("PCR-NEWER001", "PCR-OLDER001");
    }

    @Test
    void organisation_should_be_null_without_a_production_request() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "Grace", "Hopper")));
        when(applicationRepository.findByUserIdOrderByCreatedAtAsc(2L)).thenReturn(List.of());
        when(productionRequestRepository.findByUserIdOrderBySubmittedAtDesc(2L)).thenReturn(List.of());

        assertThat(service.getUser(2L).organisation()).isNull();
    }

    @Test
    void unknown_user_should_be_not_found() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(9L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static ProductionRequestEntity request(final String reference, final String organisation) {
        final ProductionRequestEntity request = new ProductionRequestEntity();
        request.setReference(reference);
        request.setOrganisation(organisation);
        request.setApplicationName("Case Viewer");
        request.setStatus("SUBMITTED");
        request.setSubmittedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        return request;
    }

    @Test
    void listing_should_keep_the_repositorys_order_and_count_applications() {
        final UserEntity grace = user(2L, "Grace", "Hopper");
        final UserEntity ada = user(1L, "Ada", "Lovelace");
        when(userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE)).thenReturn(List.of(grace, ada));
        when(applicationRepository.countByUser()).thenReturn(List.of(count(2L, 3)));

        final List<AdminUserSummary> users = service.listUsers();

        assertThat(users).extracting(AdminUserSummary::name).containsExactly("Grace Hopper", "Ada Lovelace");
        assertThat(users).extracting(AdminUserSummary::applicationCount).containsExactly(3L, 0L);
    }

    @Test
    void user_with_no_last_name_should_be_named_without_a_trailing_space() {
        when(userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE))
            .thenReturn(List.of(user(3L, "Cher", "")));
        when(applicationRepository.countByUser()).thenReturn(List.of());

        assertThat(service.listUsers().get(0).name()).isEqualTo("Cher");
    }

    private static UserEntity user(final long id, final String first, final String last) {
        final UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(first.toLowerCase() + "@example.com");
        user.setStatus(UserEntity.ACTIVE);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        user.setLastLoginAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        return user;
    }

    private static UserApplicationCount count(final long userId, final long total) {
        return new UserApplicationCount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void application_list_should_keep_the_repositorys_order_and_count_apis_and_requests() {
        final UserEntity grace = user(2L, "Grace", "Hopper");
        when(applicationRepository.findAllByOrderByCreatedAtDesc())
            .thenReturn(List.of(application(11L, "Listings", grace), application(10L, "Case Viewer", grace)));
        when(applicationApiRepository.countByApplication()).thenReturn(List.of(apiCount(10L, 2)));
        when(productionRequestRepository.findRequestedApplicationIds()).thenReturn(Set.of(10L));

        final List<AdminApplicationSummary> applications = service.listApplications();

        assertThat(applications).extracting(AdminApplicationSummary::name).containsExactly("Listings", "Case Viewer");
        assertThat(applications).extracting(AdminApplicationSummary::apiCount).containsExactly(0L, 2L);
        assertThat(applications).extracting(AdminApplicationSummary::productionRequested).containsExactly(false, true);
        assertThat(applications.get(0).owner()).isEqualTo(
            new AdminApplicationSummary.Owner(2L, "Grace Hopper", "grace@example.com"));
    }

    @Test
    void application_should_name_its_apis_and_carry_its_latest_request() {
        when(applicationRepository.findById(10L))
            .thenReturn(Optional.of(application(10L, "Case Viewer", user(2L, "Grace", "Hopper"))));
        when(applicationApiRepository.findByApplicationIdOrderBySubscribedAtAsc(10L))
            .thenReturn(List.of(subscription("slc"), subscription("gone")));
        when(marketplaceApiRepository.findAllById(any())).thenReturn(List.of(marketplaceApi("slc", "Court Schedule")));
        when(productionRequestRepository.findByApplicationIdOrderBySubmittedAtDesc(10L))
            .thenReturn(List.of(request("PCR-NEWER001", "Example Legal Ltd"), request("PCR-OLDER001", "Old Org")));

        final AdminApplicationDetail application = service.getApplication(10L);

        assertThat(application.clientId()).isEqualTo("client-10");
        assertThat(application.apis()).containsExactly("Court Schedule", "gone");
        assertThat(application.productionRequest().reference()).isEqualTo("PCR-NEWER001");
    }

    @Test
    void application_without_a_request_should_have_none() {
        when(applicationRepository.findById(10L))
            .thenReturn(Optional.of(application(10L, "Case Viewer", user(2L, "Grace", "Hopper"))));
        when(applicationApiRepository.findByApplicationIdOrderBySubscribedAtAsc(10L)).thenReturn(List.of());
        when(marketplaceApiRepository.findAllById(any())).thenReturn(List.of());
        when(productionRequestRepository.findByApplicationIdOrderBySubmittedAtDesc(10L)).thenReturn(List.of());

        final AdminApplicationDetail application = service.getApplication(10L);

        assertThat(application.apis()).isEmpty();
        assertThat(application.productionRequest()).isNull();
    }

    @Test
    void unknown_application_should_be_not_found() {
        when(applicationRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplication(9L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static ApplicationEntity application(final long id, final String name, final UserEntity owner) {
        final ApplicationEntity application = new ApplicationEntity();
        application.setId(id);
        application.setName(name);
        application.setUser(owner);
        application.setClientId("client-" + id);
        application.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        return application;
    }

    private static ApplicationApiEntity subscription(final String shortCode) {
        final ApplicationApiEntity api = new ApplicationApiEntity();
        api.setShortCode(shortCode);
        return api;
    }

    private static MarketplaceApiEntity marketplaceApi(final String shortCode, final String title) {
        final MarketplaceApiEntity api = new MarketplaceApiEntity();
        api.setShortCode(shortCode);
        api.setTitle(title);
        return api;
    }

    private static ApplicationApiCount apiCount(final long applicationId, final long total) {
        return new ApplicationApiCount() {
            @Override
            public Long getApplicationId() {
                return applicationId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
