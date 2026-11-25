package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}