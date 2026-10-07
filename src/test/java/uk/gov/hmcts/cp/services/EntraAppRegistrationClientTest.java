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
import java.time.Duration;
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
class EntraAppRegistrationClientTest {

    private static final String GENERIC_MESSAGE = "Could not register the application. Please try again.";
    private static final String AZURE_DETAIL = "AADSTS-internal-detail-tenant-d44f885c";

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private EntraAppRegistrationClient client;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(client, "tenantId", "tenant-1");
        ReflectionTestUtils.setField(client, "onboardingClientId", "onboarding-id");
        ReflectionTestUtils.setField(client, "onboardingClientSecret", "onboarding+secret&with=symbols");
        ReflectionTestUtils.setField(client, "retryDelay", Duration.ZERO);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(final int status, final String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    private HttpResponse<String> token() {
        return response(200, "{\"access_token\":\"graph-token\"}");
    }

    private HttpResponse<String> application() {
        return response(201, "{\"id\":\"object-1\",\"appId\":\"client-1\"}");
    }

    private HttpResponse<String> servicePrincipal() {
        return response(201, "{}");
    }

    private HttpResponse<String> password() {
        return response(200, "{\"keyId\":\"key-1\",\"secretText\":\"generated-secret\"}");
    }

    private HttpResponse<String> notYetReplicated() {
        return response(404, "{\"error\":\"" + AZURE_DETAIL + "\"}");
    }

    private HttpResponse<String> unavailable() {
        return response(503, "{\"error\":\"" + AZURE_DETAIL + "\"}");
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

    private void assertGeneric(final Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void registering_should_return_the_client_id_and_secret_from_the_three_graph_calls() throws Exception {
        send(token(), application(), servicePrincipal(), password());

        EntraAppRegistrationClient.Registration registration = client.register("My App");

        assertThat(registration.clientId()).isEqualTo("client-1");
        assertThat(registration.clientSecret()).isEqualTo("generated-secret");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(4)).send(requests.capture(), any());
        List<HttpRequest> sent = requests.getAllValues();
        assertThat(sent.get(0).uri().toString())
            .isEqualTo("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token");
        assertThat(sent.get(1).uri().toString()).isEqualTo("https://graph.microsoft.com/v1.0/applications");
        assertThat(sent.get(2).uri().toString()).isEqualTo("https://graph.microsoft.com/v1.0/servicePrincipals");
        assertThat(sent.get(3).uri().toString())
            .isEqualTo("https://graph.microsoft.com/v1.0/applications/object-1/addPassword");
        assertThat(sent.get(0).headers().firstValue("Authorization")).isEmpty();
        assertThat(sent.get(1).headers().firstValue("Authorization")).contains("Bearer graph-token");
        assertThat(sent.get(3).headers().firstValue("Authorization")).contains("Bearer graph-token");
    }

    @Test
    void the_token_request_should_url_encode_the_client_secret() throws Exception {
        send(token(), application(), servicePrincipal(), password());

        client.register("My App");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(4)).send(requests.capture(), any());
        // A secret containing + & = would otherwise corrupt the form body and fail authentication.
        assertThat(bodyOf(requests.getAllValues().get(0))).isEqualTo(
            "grant_type=client_credentials&client_id=onboarding-id"
                + "&client_secret=onboarding%2Bsecret%26with%3Dsymbols"
                + "&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default");
    }

    @Test
    void registering_without_a_configured_credential_should_return_503_and_call_nothing() throws Exception {
        ReflectionTestUtils.setField(client, "onboardingClientSecret", "NOT_SET");

        assertThatThrownBy(() -> client.register("My App"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void service_principal_not_yet_visible_should_be_retried_until_it_is() throws Exception {
        send(token(), application(), notYetReplicated(), notYetReplicated(), servicePrincipal(), password());

        EntraAppRegistrationClient.Registration registration = client.register("My App");

        assertThat(registration.clientSecret()).isEqualTo("generated-secret");
        verify(httpClient, times(6)).send(any(HttpRequest.class), any());
    }

    @Test
    void password_call_that_keeps_failing_should_give_up_with_a_generic_message() throws Exception {
        send(token(), application(), servicePrincipal(), notYetReplicated());

        assertGeneric(() -> client.register("My App"));

        // token + application + service principal + four attempts at the password
        verify(httpClient, times(7)).send(any(HttpRequest.class), any());
    }

    @Test
    void failed_token_request_should_not_be_retried_or_leak_the_azure_response() throws Exception {
        send(response(401, "{\"error\":\"" + AZURE_DETAIL + "\"}"));

        assertThatThrownBy(() -> client.register("My App"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE);
                assertThat(e.getReason()).doesNotContain(AZURE_DETAIL).doesNotContain("tenant-1");
            });

        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void network_failure_should_give_a_generic_message() throws Exception {
        doThrow(new IOException("connection reset by graph.microsoft.com"))
            .when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> client.register("My App"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).isEqualTo(GENERIC_MESSAGE).doesNotContain("graph.microsoft.com");
            });
    }

    @Test
    void timeout_should_give_a_generic_message() throws Exception {
        doThrow(new HttpTimeoutException("request timed out"))
            .when(httpClient).send(any(HttpRequest.class), any());

        assertGeneric(() -> client.register("My App"));
    }

    @Test
    void deleting_should_remove_the_application_by_client_id_without_a_request_body() throws Exception {
        send(token(), response(204, ""));

        client.delete("client-1");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest delete = requests.getAllValues().get(1);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.uri().toString())
            .isEqualTo("https://graph.microsoft.com/v1.0/applications(appId='client-1')");
        assertThat(delete.headers().firstValue("Authorization")).contains("Bearer graph-token");
        assertThat(delete.headers().firstValue("Content-Type")).isEmpty();
        assertThat(delete.bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    @Test
    void deleting_an_application_that_fails_at_first_should_be_retried() throws Exception {
        send(token(), unavailable(), response(204, ""));

        client.delete("client-1");

        verify(httpClient, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    void deleting_an_application_that_keeps_failing_should_give_a_generic_message() throws Exception {
        send(token(), unavailable());

        assertGeneric(() -> client.delete("client-1"));
    }

    @Test
    void deleting_an_application_that_is_already_gone_should_succeed_without_a_retry() throws Exception {
        send(token(), notYetReplicated());

        client.delete("client-1");

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void registering_should_return_the_key_id_that_revoking_the_secret_needs() throws Exception {
        send(token(), application(), servicePrincipal(), password());

        EntraAppRegistrationClient.Registration registration = client.register("My App");

        assertThat(registration.keyId()).isEqualTo("key-1");
    }

    @Test
    void adding_a_secret_should_post_to_the_application_by_client_id_and_return_its_key_id() throws Exception {
        send(token(), password());

        EntraAppRegistrationClient.Secret secret = client.addSecret("client-1");

        assertThat(secret.keyId()).isEqualTo("key-1");
        assertThat(secret.secretText()).isEqualTo("generated-secret");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest add = requests.getAllValues().get(1);
        assertThat(add.method()).isEqualTo("POST");
        assertThat(add.uri().toString())
            .isEqualTo("https://graph.microsoft.com/v1.0/applications(appId='client-1')/addPassword");
        assertThat(add.headers().firstValue("Authorization")).contains("Bearer graph-token");
    }

    @Test
    void adding_a_secret_that_keeps_failing_should_give_a_generic_message() throws Exception {
        send(token(), unavailable());

        assertGeneric(() -> client.addSecret("client-1"));
    }

    @Test
    void removing_a_secret_should_post_its_key_id_to_the_application_by_client_id() throws Exception {
        send(token(), response(204, ""));

        client.removeSecret("client-1", "key-1");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest remove = requests.getAllValues().get(1);
        assertThat(remove.method()).isEqualTo("POST");
        assertThat(remove.uri().toString())
            .isEqualTo("https://graph.microsoft.com/v1.0/applications(appId='client-1')/removePassword");
        assertThat(remove.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(bodyOf(remove)).isEqualTo("{\"keyId\":\"key-1\"}");
    }

    @Test
    void removing_a_secret_that_keeps_failing_should_give_a_generic_message() throws Exception {
        send(token(), unavailable());

        assertGeneric(() -> client.removeSecret("client-1", "key-1"));
    }

    @Test
    void secrets_should_not_be_added_or_removed_without_a_configured_credential() throws Exception {
        ReflectionTestUtils.setField(client, "onboardingClientId", "NOT_SET");

        assertThatThrownBy(() -> client.addSecret("client-1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> client.removeSecret("client-1", "key-1"))
            .isInstanceOf(ResponseStatusException.class);

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void stand_in_for_graph_and_the_sign_in_endpoint_should_be_used_when_configured() throws Exception {
        ReflectionTestUtils.setField(client, "graphBase", "http://stand-in:8080/graph");
        ReflectionTestUtils.setField(client, "loginBase", "http://stand-in:8080");
        send(token(), response(204, ""));

        client.delete("client-1");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        assertThat(requests.getAllValues().get(0).uri().toString())
            .isEqualTo("http://stand-in:8080/tenant-1/oauth2/v2.0/token");
        assertThat(requests.getAllValues().get(1).uri().toString())
            .isEqualTo("http://stand-in:8080/graph/applications(appId='client-1')");
    }

    @Test
    void deleting_without_a_configured_credential_should_return_503_and_call_nothing() throws Exception {
        ReflectionTestUtils.setField(client, "onboardingClientSecret", "NOT_SET");

        assertThatThrownBy(() -> client.delete("client-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void an_interrupt_should_give_a_generic_message_and_leave_the_thread_interrupted() throws Exception {
        doThrow(new InterruptedException("shutting down"))
            .when(httpClient).send(any(HttpRequest.class), any());

        try {
            assertGeneric(() -> client.register("My App"));
            // Swallowing the interrupt would hide a pod shutdown from everything above this call.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
