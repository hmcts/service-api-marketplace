package uk.gov.hmcts.cp.services;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.apimanagement.ApiManagementManager;
import com.azure.resourcemanager.apimanagement.models.ProductContract;
import com.azure.resourcemanager.apimanagement.models.ProductState;
import com.azure.resourcemanager.apimanagement.models.Products;
import com.azure.resourcemanager.apimanagement.models.SubscriptionContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionCreateParameters;
import com.azure.resourcemanager.apimanagement.models.SubscriptionKeysContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionState;
import com.azure.resourcemanager.apimanagement.models.Subscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApimProduct;
import uk.gov.hmcts.cp.domain.SubscriptionKey;

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

    private Subscriptions subscriptions;
    private Products products;
    private ApimSubscriptionKeyService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(Subscriptions.class);
        products = mock(Products.class);
        ApiManagementManager apim = mock(ApiManagementManager.class);
        when(apim.subscriptions()).thenReturn(subscriptions);
        lenientProducts(apim);
        service = new ApimSubscriptionKeyService(apim);
        ReflectionTestUtils.setField(service, "resourceGroup", RESOURCE_GROUP);
        ReflectionTestUtils.setField(service, "serviceName", SERVICE_NAME);
    }

    @Test
    void listing_keys_should_not_ask_azure_for_any_secret() {
        givenListed(contract("team-alpha", INSTANCE_SCOPE + "/products/hearing-results"));

        List<SubscriptionKey> listed = service.list();

        assertThat(listed).extracting(SubscriptionKey::name).containsExactly("team-alpha");
        assertThat(listed).extracting(SubscriptionKey::primaryKey).containsOnlyNulls();
        verify(subscriptions, never()).listSecrets(any(), any(), any());
    }

    @Test
    void listing_keys_should_lift_the_product_id_out_of_the_scope() {
        givenListed(contract("team-alpha", INSTANCE_SCOPE + "/products/hearing-results"));

        assertThat(service.list()).singleElement()
            .extracting(SubscriptionKey::productId).isEqualTo("hearing-results");
    }

    @Test
    void key_scoped_to_the_whole_service_should_have_no_product_id() {
        givenListed(contract("master", INSTANCE_SCOPE + "/"));

        assertThat(service.list()).singleElement()
            .extracting(SubscriptionKey::productId).isNull();
    }

    @Test
    void creating_a_key_should_scope_it_to_the_product_and_return_both_values() {
        givenAbsent("team-alpha");
        SubscriptionContract created = contract("team-alpha", INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.createOrUpdate(eq(RESOURCE_GROUP), eq(SERVICE_NAME), eq("team-alpha"), any()))
            .thenReturn(created);
        givenSecretsFor("team-alpha");

        SubscriptionKey result = service.create("team-alpha", "hearing-results", "Team Alpha");

        ArgumentCaptor<SubscriptionCreateParameters> sent =
            ArgumentCaptor.forClass(SubscriptionCreateParameters.class);
        verify(subscriptions).createOrUpdate(eq(RESOURCE_GROUP), eq(SERVICE_NAME), eq("team-alpha"),
            sent.capture());
        assertThat(sent.getValue().scope()).isEqualTo("/products/hearing-results");
        assertThat(sent.getValue().displayName()).isEqualTo("Team Alpha");
        assertThat(sent.getValue().state()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(result.primaryKey()).isEqualTo("primary-key-value");
        assertThat(result.secondaryKey()).isEqualTo("secondary-key-value");
    }

    @Test
    void creating_a_key_that_exists_should_conflict_without_writing_anything() {
        SubscriptionContract existing = contract("team-alpha", INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.get(RESOURCE_GROUP, SERVICE_NAME, "team-alpha")).thenReturn(existing);

        assertThatThrownBy(() -> service.create("team-alpha", "hearing-results", "Team Alpha"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        // Azure's createOrUpdate is a PUT and would have upserted over the existing key.
        verify(subscriptions, never()).createOrUpdate(any(), any(), any(), any());
    }

    @Test
    void reading_key_values_should_return_the_values_and_nothing_else() {
        givenSecretsFor("team-alpha");

        SubscriptionKey values = service.keyValues("team-alpha");

        assertThat(values.primaryKey()).isEqualTo("primary-key-value");
        assertThat(values.secondaryKey()).isEqualTo("secondary-key-value");
        assertThat(values.scope()).isNull();
        assertThat(values.displayName()).isNull();
    }

    @Test
    void deleting_a_key_should_pass_the_if_match_azure_insists_on() {
        SubscriptionContract existing = contract("team-alpha", INSTANCE_SCOPE + "/products/hearing-results");
        when(subscriptions.get(RESOURCE_GROUP, SERVICE_NAME, "team-alpha")).thenReturn(existing);

        service.delete("team-alpha");

        verify(subscriptions).delete(RESOURCE_GROUP, SERVICE_NAME, "team-alpha", "*");
    }

    @Test
    void deleting_a_key_that_is_not_there_should_be_not_found_without_calling_delete() {
        givenAbsent("nobody");

        assertThatThrownBy(() -> service.delete("nobody"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(subscriptions, never()).delete(any(), any(), any(), any());
    }

    @Test
    void not_found_should_name_the_instance_it_looked_in() {
        givenAbsent("nobody");

        assertThatThrownBy(() -> service.delete("nobody"))
            .hasMessageContaining("nobody")
            .hasMessageContaining(SERVICE_NAME)
            .hasMessageContaining(RESOURCE_GROUP);
    }

    @Test
    void refusal_should_be_reported_as_our_fault_rather_than_a_bad_gateway() {
        ManagementException refused = azureStatus(403);
        when(subscriptions.listSecrets(RESOURCE_GROUP, SERVICE_NAME, "team-alpha")).thenThrow(refused);

        assertThatThrownBy(() -> service.keyValues("team-alpha"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(e.getReason()).doesNotContain(RESOURCE_GROUP);
            });
    }

    @Test
    void any_other_azure_failure_should_be_a_bad_gateway_without_azure_detail() {
        ManagementException failed = new ManagementException("quota exceeded on " + RESOURCE_GROUP, response(500));
        when(subscriptions.listSecrets(RESOURCE_GROUP, SERVICE_NAME, "team-alpha")).thenThrow(failed);

        assertThatThrownBy(() -> service.keyValues("team-alpha"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).doesNotContain(RESOURCE_GROUP).doesNotContain("quota");
            });
    }

    @Test
    @SuppressWarnings("unchecked") // mock() of the SDK's generic PagedIterable
    void listing_products_should_return_the_ids_create_accepts() {
        ProductContract product = mock(ProductContract.class);
        when(product.name()).thenReturn("hearing-results");
        when(product.displayName()).thenReturn("Hearing Results");
        when(product.description()).thenReturn(null);
        when(product.state()).thenReturn(ProductState.PUBLISHED);
        PagedIterable<ProductContract> page = mock(PagedIterable.class);
        when(page.stream()).thenReturn(Stream.of(product));
        when(products.listByService(RESOURCE_GROUP, SERVICE_NAME)).thenReturn(page);

        List<ApimProduct> listed = service.listProducts();

        assertThat(listed).singleElement().satisfies(p -> {
            assertThat(p.productId()).isEqualTo("hearing-results");
            assertThat(p.displayName()).isEqualTo("Hearing Results");
            assertThat(p.description()).isNull();
            assertThat(p.state()).isEqualTo("published");
        });
    }

    private void lenientProducts(final ApiManagementManager apim) {
        lenient().when(apim.products()).thenReturn(products);
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
