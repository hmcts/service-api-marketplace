package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Stubber;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApimSubscriptionClientTest {

    private static final String GENERIC_MESSAGE = "Could not issue an API key. Please try again.";
    private static final String AZURE_SUBSCRIPTION = "11111111-2222-3333-4444-555555555555";

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private ApimSubscriptionClient client;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(client, "tenantId", "tenant-1");
        ReflectionTestUtils.setField(client, "clientId", "apim-id");
        ReflectionTestUtils.setField(client, "clientSecret", "apim+secret&with=symbols");
        ReflectionTestUtils.setField(client, "azureSubscriptionId", AZURE_SUBSCRIPTION);
        ReflectionTestUtils.setField(client, "resourceGroup", "rg-test");
        ReflectionTestUtils.setField(client, "serviceName", "apim-test");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(final int status, final String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    private HttpResponse<String> token() {
        return response(200, "{\"access_token\":\"arm-token\"}");
    }

    private HttpResponse<String> subscription() {
        return response(200, "{\"properties\":{\"primaryKey\":\"the-primary-key\"}}");
    }

    @SafeVarargs
    private void send(final HttpResponse<String> first, final HttpResponse<String>... rest) throws Exception {
        Stubber stubber = doReturn(first);
        for (HttpResponse<String> next : rest) {
            stubber = stubber.doReturn(next);
        }
        stubber.when(httpClient).send(any(HttpRequest.class), any());
    }

    private String bodyOf(final HttpRequest request) throws Exception {
        CompletableFuture<String> body = new CompletableFuture<>();
        StringBuilder collected = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                collected.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(final Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(collected.toString());
            }
        });
        return body.get(2, TimeUnit.SECONDS);
    }

    @Test
    void creating_a_subscription_should_return_the_product_and_primary_key() throws Exception {
        send(token(), subscription());

        ApimSubscriptionClient.Subscription result = client.createSubscription("My App", "product-pcd");

        assertThat(result.publisherId()).isEqualTo("product-pcd");
        assertThat(result.subscriptionKey()).isEqualTo("the-primary-key");
        assertThat(result.subscriptionName()).matches("my-app-[0-9a-f]{8}");
    }

    @Test
    void the_returned_name_should_be_the_one_the_subscription_was_created_under() throws Exception {
        send(token(), subscription());

        ApimSubscriptionClient.Subscription result = client.createSubscription("My App", "product-pcd");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        // This is what deleteSubscription is later handed, so it must match what was actually created.
        assertThat(requests.getAllValues().get(1).uri().getPath())
            .endsWith("/subscriptions/" + result.subscriptionName());
    }

    @Test
    void the_subscription_should_be_put_against_the_configured_instance_and_scoped_to_the_product() throws Exception {
        send(token(), subscription());

        client.createSubscription("My App", "product-pcd");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        List<HttpRequest> sent = requests.getAllValues();

        assertThat(sent.get(0).uri().toString())
            .isEqualTo("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token");
        assertThat(bodyOf(sent.get(0))).isEqualTo(
            "grant_type=client_credentials&client_id=apim-id"
                + "&client_secret=apim%2Bsecret%26with%3Dsymbols"
                + "&scope=https%3A%2F%2Fmanagement.azure.com%2F.default");

        HttpRequest put = sent.get(1);
        assertThat(put.method()).isEqualTo("PUT");
        assertThat(put.headers().firstValue("Authorization")).contains("Bearer arm-token");
        assertThat(put.uri().toString())
            .startsWith("https://management.azure.com/subscriptions/" + AZURE_SUBSCRIPTION
                + "/resourceGroups/rg-test/providers/Microsoft.ApiManagement/service/apim-test/subscriptions/")
            .endsWith("?api-version=2022-08-01");
        assertThat(bodyOf(put)).contains("\"scope\":\"/products/product-pcd\"");
    }

    @Test
    void the_subscription_name_should_be_a_slug_of_the_application_name_with_a_unique_suffix() throws Exception {
        send(token(), subscription(), token(), subscription());

        client.createSubscription("My App", "product-pcd");
        client.createSubscription("My App", "product-pcd");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(4)).send(requests.capture(), any());
        String first = requests.getAllValues().get(1).uri().getPath();
        String second = requests.getAllValues().get(3).uri().getPath();

        assertThat(first).matches(".*/subscriptions/my-app-[0-9a-f]{8}");
        // Adding the same API to the same application twice must not collide with the first name.
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void creating_a_subscription_without_a_configured_credential_should_return_503_and_call_nothing()
        throws Exception {
        ReflectionTestUtils.setField(client, "clientSecret", "NOT_SET");

        assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void an_arm_error_should_give_a_generic_message_without_the_azure_response_or_identifiers() throws Exception {
        send(token(), response(409, "{\"error\":{\"message\":\"subscription " + AZURE_SUBSCRIPTION + " conflict\"}}"));

        assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
                assertThat(e.getReason()).doesNotContain(AZURE_SUBSCRIPTION).doesNotContain("rg-test");
            });
    }

    @Test
    void failed_token_request_should_give_a_generic_message() throws Exception {
        send(response(401, "{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
            });

        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void network_failure_should_give_a_generic_message() throws Exception {
        doThrow(new IOException("connection reset by management.azure.com"))
            .when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE).doesNotContain("management.azure.com");
            });
    }

    @Test
    void timeout_should_give_a_generic_message() throws Exception {
        doThrow(new HttpTimeoutException("request timed out"))
            .when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
            });
    }

    @Test
    void deleting_a_subscription_should_delete_it_by_name_without_a_request_body() throws Exception {
        send(token(), response(200, ""));

        client.deleteSubscription("my-app-1a2b3c4d");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest delete = requests.getAllValues().get(1);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.uri().toString()).isEqualTo("https://management.azure.com/subscriptions/"
            + AZURE_SUBSCRIPTION + "/resourceGroups/rg-test/providers/Microsoft.ApiManagement/service/apim-test"
            + "/subscriptions/my-app-1a2b3c4d?api-version=2022-08-01");
        assertThat(delete.headers().firstValue("Authorization")).contains("Bearer arm-token");
        assertThat(delete.headers().firstValue("Content-Type")).isEmpty();
        assertThat(delete.bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    @Test
    void deleting_without_a_configured_credential_should_return_503_and_call_nothing() throws Exception {
        ReflectionTestUtils.setField(client, "clientId", "NOT_SET");

        assertThatThrownBy(() -> client.deleteSubscription("my-app-1a2b3c4d"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void failed_delete_should_give_a_generic_message_without_azure_detail() throws Exception {
        send(token(), response(403, "{\"error\":\"" + AZURE_SUBSCRIPTION + " forbidden\"}"));

        assertThatThrownBy(() -> client.deleteSubscription("my-app-1a2b3c4d"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
                assertThat(e.getReason()).doesNotContain(AZURE_SUBSCRIPTION);
            });
    }

    @Test
    void an_interrupt_should_give_a_generic_message_and_leave_the_thread_interrupted() throws Exception {
        doThrow(new InterruptedException("shutting down"))
            .when(httpClient).send(any(HttpRequest.class), any());

        try {
            assertThatThrownBy(() -> client.createSubscription("My App", "product-pcd"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
                });
            // Swallowing the interrupt would hide a pod shutdown from everything above this call.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
