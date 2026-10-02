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
        return response(200, "{\"secretText\":\"generated-secret\"}");
    }

    private HttpResponse<String> notYetReplicated() {
        return response(404, "{\"error\":\"" + AZURE_DETAIL + "\"}");
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
}
