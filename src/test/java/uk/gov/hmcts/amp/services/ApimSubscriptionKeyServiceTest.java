package uk.gov.hmcts.amp.services;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.azure.resourcemanager.apimanagement.models.SubscriptionContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionCreateParameters;
import com.azure.resourcemanager.apimanagement.models.SubscriptionKeysContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionState;
import com.azure.resourcemanager.apimanagement.models.Subscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.ApimProperties;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.entity.UserEntity;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApimSubscriptionKeyServiceTest {

    private static final String RESOURCE_GROUP = "rg-test";
    private static final String SERVICE_NAME = "apim-test";
    private static final String INSTANCE_SCOPE =
        "/subscriptions/sub/resourceGroups/rg-test/providers/Microsoft.ApiManagement/service/apim-test";
    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String NAME = "amp-user001-team-alpha";
    private static final String ABSENT = "amp-user001-nobody";
    private static final String SOMEONE_ELSES = "amp-user002-team-beta";

    private Subscriptions subscriptions;
    private ApimSubscriptionKeyService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(Subscriptions.class);
        ApiManagementManager apim = mock(ApiManagementManager.class);
        when(apim.subscriptions()).thenReturn(subscriptions);
        UserEntity caller = new UserEntity();
        caller.setId(1L);
        UserService users = mock(UserService.class);
        lenient().when(users.requireSignedIn(OID)).thenReturn(caller);
        service = new ApimSubscriptionKeyService(apim, users,
            new ApimProperties("a-tenant", "a-subscription", RESOURCE_GROUP, SERVICE_NAME));
    }

    @Test
    void listing_keys_should_not_ask_azure_for_any_secret() {
        givenListed(contract(NAME, INSTANCE_SCOPE + "/products/hearing-results"));

        List<SubscriptionKey> listed = service.list(OID);

        assertThat(listed).extracting(SubscriptionKey::name).containsExactly(NAME);
        assertThat(listed).extracting(SubscriptionKey::primaryKey).containsOnlyNulls();
        verify(subscriptions, never()).listSecrets(any(), any(), any());
    }

    @Test
    void listing_keys_should_lift_the_product_id_out_of_the_scope() {
        givenListed(contract(NAME, INSTANCE_SCOPE + "/products/hearing-results"));

        assertThat(service.list(OID)).singleElement()
            .extracting(SubscriptionKey::productId).isEqualTo("hearing-results");
    }

    @Test
    void key_scoped_to_the_whole_service_should_have_no_product_id() {
        givenListed(contract(NAME, INSTANCE_SCOPE + "/"));

        assertThat(service.list(OID)).singleElement()
            .extracting(SubscriptionKey::productId).isNull();
    }

    @Test
    void creating_a_key_should_scope_it_to_the_product_and_return_both_values() {
        givenAbsent(NAME);
        SubscriptionContract created = contract(NAME, INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.createOrUpdate(eq(RESOURCE_GROUP), eq(SERVICE_NAME), eq(NAME), any()))
            .thenReturn(created);
        givenSecretsFor(NAME);

        SubscriptionKey result = service.create(OID, NAME, "hearing-results", "Team Alpha");

        ArgumentCaptor<SubscriptionCreateParameters> sent =
            ArgumentCaptor.forClass(SubscriptionCreateParameters.class);
        verify(subscriptions).createOrUpdate(eq(RESOURCE_GROUP), eq(SERVICE_NAME), eq(NAME),
            sent.capture());
        assertThat(sent.getValue().scope()).isEqualTo("/products/hearing-results");
        assertThat(sent.getValue().displayName()).isEqualTo("Team Alpha");
        assertThat(sent.getValue().state()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(result.primaryKey()).isEqualTo("primary-key-value");
        assertThat(result.secondaryKey()).isEqualTo("secondary-key-value");
    }

    @Test
    void creating_a_key_that_exists_should_conflict_without_writing_anything() {
        SubscriptionContract existing = contract(NAME, INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.get(RESOURCE_GROUP, SERVICE_NAME, NAME)).thenReturn(existing);

        assertThatThrownBy(() -> service.create(OID, NAME, "hearing-results", "Team Alpha"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        // Azure's createOrUpdate is a PUT and would have upserted over the existing key.
        verify(subscriptions, never()).createOrUpdate(any(), any(), any(), any());
    }

    @Test
    void reading_key_values_should_return_the_values_and_nothing_else() {
        givenSecretsFor(NAME);

        SubscriptionKey values = service.keyValues(OID, NAME);

        assertThat(values.primaryKey()).isEqualTo("primary-key-value");
        assertThat(values.secondaryKey()).isEqualTo("secondary-key-value");
        assertThat(values.scope()).isNull();
        assertThat(values.displayName()).isNull();
    }

    @Test
    void deleting_a_key_should_pass_the_if_match_azure_insists_on() {
        SubscriptionContract existing = contract(NAME, INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.get(RESOURCE_GROUP, SERVICE_NAME, NAME)).thenReturn(existing);

        service.delete(OID, NAME);

        verify(subscriptions).delete(RESOURCE_GROUP, SERVICE_NAME, NAME, "*");
    }

    @Test
    void deleting_a_key_that_is_not_there_should_be_not_found_without_calling_delete() {
        givenAbsent(ABSENT);

        assertThatThrownBy(() -> service.delete(OID, ABSENT))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(subscriptions, never()).delete(any(), any(), any(), any());
    }

    @Test
    void not_found_should_name_the_instance_it_looked_in() {
        givenAbsent(ABSENT);

        assertThatThrownBy(() -> service.delete(OID, ABSENT))
            .hasMessageContaining(ABSENT)
            .hasMessageContaining(SERVICE_NAME)
            .hasMessageContaining(RESOURCE_GROUP);
    }

    @Test
    void refusal_should_be_reported_as_our_fault_rather_than_a_bad_gateway() {
        ManagementException refused = azureStatus(403);
        when(subscriptions.listSecrets(RESOURCE_GROUP, SERVICE_NAME, NAME)).thenThrow(refused);

        assertThatThrownBy(() -> service.keyValues(OID, NAME))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(e.getReason()).doesNotContain(RESOURCE_GROUP);
            });
    }

    @Test
    void any_other_azure_failure_should_be_a_bad_gateway_without_azure_detail() {
        ManagementException failed = new ManagementException("quota exceeded on " + RESOURCE_GROUP, response(500));
        when(subscriptions.listSecrets(RESOURCE_GROUP, SERVICE_NAME, NAME)).thenThrow(failed);

        assertThatThrownBy(() -> service.keyValues(OID, NAME))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).doesNotContain(RESOURCE_GROUP).doesNotContain("quota");
            });
    }


    @Test
    void listing_should_leave_out_subscriptions_belonging_to_someone_else() {
        givenListed(contract(NAME, INSTANCE_SCOPE + "/products/hearing-results"),
            contract(SOMEONE_ELSES, INSTANCE_SCOPE + "/products/hearing-results"));

        assertThat(service.list(OID)).extracting(SubscriptionKey::name).containsExactly(NAME);
    }

    @Test
    void creating_a_name_outside_your_own_should_be_refused_before_azure() {
        assertThatThrownBy(() -> service.create(OID, SOMEONE_ELSES, "hearing-results", "Team Beta"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
            .hasMessageContaining("amp-user001-");
        verify(subscriptions, never()).createOrUpdate(any(), any(), any(), any());
    }

    @Test
    void reading_someone_elses_key_values_should_be_not_found_without_reading_the_secret() {
        assertThatThrownBy(() -> service.keyValues(OID, SOMEONE_ELSES))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(subscriptions, never()).listSecrets(any(), any(), any());
    }

    @Test
    void deleting_someone_elses_subscription_should_be_not_found_without_touching_azure() {
        assertThatThrownBy(() -> service.delete(OID, SOMEONE_ELSES))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(subscriptions, never()).delete(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked") // mock() of the SDK's generic PagedIterable
    private void givenListed(final SubscriptionContract... contracts) {
        PagedIterable<SubscriptionContract> page = mock(PagedIterable.class);
        when(page.stream()).thenReturn(Stream.of(contracts));
        when(subscriptions.list(RESOURCE_GROUP, SERVICE_NAME)).thenReturn(page);
    }

    private void givenAbsent(final String name) {
        ManagementException notFound = azureStatus(404);
        when(subscriptions.get(RESOURCE_GROUP, SERVICE_NAME, name)).thenThrow(notFound);
    }

    private void givenSecretsFor(final String name) {
        SubscriptionKeysContract secrets = mock(SubscriptionKeysContract.class);
        when(secrets.primaryKey()).thenReturn("primary-key-value");
        when(secrets.secondaryKey()).thenReturn("secondary-key-value");
        when(subscriptions.listSecrets(RESOURCE_GROUP, SERVICE_NAME, name)).thenReturn(secrets);
    }

    private SubscriptionContract contract(final String name, final String scope) {
        SubscriptionContract contract = mock(SubscriptionContract.class);
        when(contract.name()).thenReturn(name);
        lenient().when(contract.displayName()).thenReturn("Team Alpha");
        when(contract.scope()).thenReturn(scope);
        lenient().when(contract.state()).thenReturn(SubscriptionState.ACTIVE);
        return contract;
    }

    private ManagementException azureStatus(final int statusCode) {
        return new ManagementException("Azure said " + statusCode, response(statusCode));
    }

    private HttpResponse response(final int statusCode) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(statusCode);
        return response;
    }
}
