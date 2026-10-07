package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ApplicationService applicationService;

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");
    private static final EntraAppRegistrationClient.Registration REGISTRATION =
        new EntraAppRegistrationClient.Registration("client-id", "client-secret", "key-id");

    private final UserEntity user = UserEntity.builder().id(1).build();

    private final ApplicationRequest request = ApplicationRequest.builder()
        .name("Test App")
        .environment("sandbox")
        .apiShortCodes(List.of("PCD", "SLC"))
        .build();

    @BeforeEach
    void runTransactionalWorkInline() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
            invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().when(userRepository.findById(1)).thenReturn(Optional.of(user));
        lenient().when(clockService.now()).thenReturn(NOW);
    }

    private void knownProducts() {
        when(apimProductRegistry.productIdFor("PCD")).thenReturn("product-pcd");
        when(apimProductRegistry.productIdFor("SLC")).thenReturn("product-slc");
    }

    private ApimSubscriptionClient.Subscription issued(final String product) {
        return new ApimSubscriptionClient.Subscription(product, "key-" + product, "sub-" + product);
    }

    private void stubSuccessfulPersistence() {
        ApplicationEntity saved = ApplicationEntity.builder().id(10L).name("Test App").build();
        when(applicationRepository.save(any(ApplicationEntity.class))).thenReturn(saved);
        when(applicationApiKeyRepository.save(any(ApplicationApiKeyEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationMapper.toResponse(eq(saved), anyList(), eq("client-secret")))
            .thenReturn(ApplicationResponse.builder().clientId("client-id").build());
    }

    @Test
    void registering_should_issue_one_key_per_api_and_save_the_application_and_its_keys() {
        knownProducts();
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd")).thenReturn(issued("product-pcd"));
        when(apimSubscriptionClient.createSubscription("Test App", "product-slc")).thenReturn(issued("product-slc"));
        stubSuccessfulPersistence();

        ApplicationResponse response = applicationService.register(1, request);

        assertThat(response.getClientId()).isEqualTo("client-id");
        ArgumentCaptor<ApplicationApiKeyEntity> keys = ArgumentCaptor.forClass(ApplicationApiKeyEntity.class);
        verify(applicationApiKeyRepository, times(2)).save(keys.capture());
        assertThat(keys.getAllValues())
            .extracting(ApplicationApiKeyEntity::getApiShortCode, ApplicationApiKeyEntity::getPublisherId,
                ApplicationApiKeyEntity::getSubscriptionKey)
            .containsExactly(
                tuple("PCD", "product-pcd", "key-product-pcd"),
                tuple("SLC", "product-slc", "key-product-slc"));
        verify(entraAppRegistrationClient, never()).delete(anyString());
        verify(apimSubscriptionClient, never()).deleteSubscription(anyString());
    }

    @Test
    void every_short_code_should_be_validated_before_anything_external_is_created() {
        when(apimProductRegistry.productIdFor("PCD")).thenReturn("product-pcd");
        when(apimProductRegistry.productIdFor("SLC"))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown apiShortCode: SLC"));

        assertThatThrownBy(() -> applicationService.register(1, request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        // The valid first code must not have caused an Entra application to be created.
        verifyNoInteractions(entraAppRegistrationClient, apimSubscriptionClient, applicationRepository,
            applicationApiKeyRepository);
    }

    @Test
    void asking_for_the_same_api_twice_should_issue_one_key() {
        when(apimProductRegistry.productIdFor("PCD")).thenReturn("product-pcd");
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd")).thenReturn(issued("product-pcd"));
        stubSuccessfulPersistence();

        applicationService.register(1, request.toBuilder().apiShortCodes(List.of("PCD", "PCD")).build());

        verify(apimSubscriptionClient, times(1)).createSubscription(anyString(), anyString());
    }

    @Test
    void failure_on_a_later_api_should_delete_the_earlier_key_and_the_entra_application() {
        knownProducts();
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd")).thenReturn(issued("product-pcd"));
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not issue.");
        when(apimSubscriptionClient.createSubscription("Test App", "product-slc")).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.register(1, request)).isSameAs(failure);

        InOrder order = inOrder(apimSubscriptionClient, entraAppRegistrationClient);
        order.verify(apimSubscriptionClient).deleteSubscription("sub-product-pcd");
        order.verify(entraAppRegistrationClient).delete("client-id");
        verify(apimSubscriptionClient, never()).deleteSubscription("sub-product-slc");
        verifyNoInteractions(applicationRepository, applicationApiKeyRepository);
    }

    @Test
    void failure_on_the_first_api_should_delete_only_the_entra_application() {
        knownProducts();
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd"))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not issue."));

        assertThatThrownBy(() -> applicationService.register(1, request))
            .isInstanceOf(ResponseStatusException.class);

        verify(entraAppRegistrationClient).delete("client-id");
        verify(apimSubscriptionClient, never()).deleteSubscription(anyString());
    }

    @Test
    void failure_saving_to_the_database_should_delete_every_key_and_the_entra_application() {
        knownProducts();
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd")).thenReturn(issued("product-pcd"));
        when(apimSubscriptionClient.createSubscription("Test App", "product-slc")).thenReturn(issued("product-slc"));
        IllegalStateException failure = new IllegalStateException("database is down");
        when(applicationRepository.save(any(ApplicationEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.register(1, request)).isSameAs(failure);

        verify(apimSubscriptionClient).deleteSubscription("sub-product-pcd");
        verify(apimSubscriptionClient).deleteSubscription("sub-product-slc");
        verify(entraAppRegistrationClient).delete("client-id");
    }

    @Test
    void clean_up_failure_should_not_mask_the_original_error_or_stop_the_rest_of_the_clean_up() {
        knownProducts();
        when(entraAppRegistrationClient.register("Test App")).thenReturn(REGISTRATION);
        when(apimSubscriptionClient.createSubscription("Test App", "product-pcd")).thenReturn(issued("product-pcd"));
        ResponseStatusException original = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not issue.");
        when(apimSubscriptionClient.createSubscription("Test App", "product-slc")).thenThrow(original);
        doThrow(new IllegalStateException("apim delete failed"))
            .when(apimSubscriptionClient).deleteSubscription("sub-product-pcd");
        doThrow(new IllegalStateException("graph delete failed"))
            .when(entraAppRegistrationClient).delete("client-id");

        assertThatThrownBy(() -> applicationService.register(1, request)).isSameAs(original);

        verify(apimSubscriptionClient).deleteSubscription("sub-product-pcd");
        verify(entraAppRegistrationClient).delete("client-id");
    }

    @Test
    void failure_creating_the_entra_application_should_have_nothing_to_clean_up() {
        knownProducts();
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not register.");
        when(entraAppRegistrationClient.register("Test App")).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.register(1, request)).isSameAs(failure);

        verify(entraAppRegistrationClient, never()).delete(anyString());
        verifyNoInteractions(apimSubscriptionClient, applicationRepository);
    }

    @Test
    void registering_for_an_unknown_user_should_throw_unauthorized_and_touch_nothing_external() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.register(99, request))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getReason()).isEqualTo("Requesting user not found.");
            });

        verifyNoInteractions(entraAppRegistrationClient, apimSubscriptionClient, apimProductRegistry);
    }

    @Test
    void getting_applications_for_a_user_should_map_each_one_with_a_null_client_secret() {
        ApplicationEntity application = ApplicationEntity.builder().id(10L).build();
        when(applicationRepository.findByUserId(1)).thenReturn(List.of(application));
        when(applicationApiKeyRepository.findByApplicationId(10L)).thenReturn(List.of());
        ApplicationResponse mappedResponse = ApplicationResponse.builder().build();
        when(applicationMapper.toResponse(application, List.of(), null)).thenReturn(mappedResponse);

        assertThat(applicationService.getForUser(1)).containsExactly(mappedResponse);
    }
}
