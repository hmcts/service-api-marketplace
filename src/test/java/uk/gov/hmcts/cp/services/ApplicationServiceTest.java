package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApplicationRequest;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.ApplicationMapper;
import uk.gov.hmcts.cp.repository.ApplicationApiKeyRepository;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationApiKeyRepository applicationApiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private ClockService clockService;

    @Mock
    private EntraAppRegistrationClient entraAppRegistrationClient;

    @Mock
    private ApimSubscriptionClient apimSubscriptionClient;

    @Mock
    private ApimProductRegistry apimProductRegistry;

    @InjectMocks
    private ApplicationService applicationService;

    private static final Instant SUBMITTED_AT = Instant.parse("2026-01-15T09:00:00Z");

    private final UserEntity user = UserEntity.builder().id(1).build();

    private final ApplicationRequest request = ApplicationRequest.builder()
        .name("Test App")
        .environment("sandbox")
        .apiShortCodes(List.of("PCD", "SLC"))
        .build();

    @Test
    void registering_should_create_one_entra_application_and_one_subscription_per_api() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(entraAppRegistrationClient.register("Test App"))
            .thenReturn(new EntraAppRegistrationClient.Registration("client-id", "client-secret"));
        when(clockService.now()).thenReturn(SUBMITTED_AT);

        ApplicationEntity savedApplication = ApplicationEntity.builder().id(10L).name("Test App").build();
        when(applicationRepository.save(any(ApplicationEntity.class))).thenReturn(savedApplication);

        when(apimProductRegistry.productIdFor("PCD")).thenReturn("product-pcd");
        when(apimProductRegistry.productIdFor("SLC")).thenReturn("product-slc");
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd"))
            .thenReturn(new ApimSubscriptionClient.Subscription("product-pcd", "key-pcd"));
        when(apimSubscriptionClient.createSubscription("Test App", "product-slc"))
            .thenReturn(new ApimSubscriptionClient.Subscription("product-slc", "key-slc"));
        when(applicationApiKeyRepository.save(any(ApplicationApiKeyEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse mappedResponse = ApplicationResponse.builder().build();
        when(applicationMapper.toResponse(eq(savedApplication), anyList(), eq("client-secret")))
            .thenReturn(mappedResponse);

        ApplicationResponse response = applicationService.register(1, request);

        assertThat(response).isEqualTo(mappedResponse);
        verify(apimSubscriptionClient).createSubscription("Test App", "product-pcd");
        verify(apimSubscriptionClient).createSubscription("Test App", "product-slc");
        verify(applicationApiKeyRepository, times(2)).save(any(ApplicationApiKeyEntity.class));
    }

    @Test
    void registering_for_an_unknown_user_should_throw_unauthorized_and_call_neither_entra_nor_apim() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.register(99, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Requesting user not found.");

        verify(entraAppRegistrationClient, never()).register(any());
        verify(apimSubscriptionClient, never()).createSubscription(any(), any());
    }

    @Test
    void getting_applications_for_a_user_should_map_each_one_with_a_null_client_secret() {
        ApplicationEntity application = ApplicationEntity.builder().id(10L).build();
        when(applicationRepository.findByUserId(1)).thenReturn(List.of(application));
        when(applicationApiKeyRepository.findByApplicationId(10L)).thenReturn(List.of());
        ApplicationResponse mappedResponse = ApplicationResponse.builder().build();
        when(applicationMapper.toResponse(application, List.of(), null)).thenReturn(mappedResponse);

        List<ApplicationResponse> responses = applicationService.getForUser(1);

        assertThat(responses).containsExactly(mappedResponse);
    }
}
