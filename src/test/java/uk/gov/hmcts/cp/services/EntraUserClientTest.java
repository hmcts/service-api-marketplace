package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
class EntraUserClientTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String GRAPH_DETAIL = "Graph-internal-detail-tenant-d44f885c";

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private EntraUserClient client;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(client, "tenantId", "tenant-1");
        ReflectionTestUtils.setField(client, "tenantDomain", "tenant.onmicrosoft.com");
        ReflectionTestUtils.setField(client, "clientId", "user-onboarding-id");
        ReflectionTestUtils.setField(client, "clientSecret", "user+secret&with=symbols");
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

    private HttpResponse<String> created() {
        return response(201, "{\"id\":\"entra-oid-1\"}");
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
        return body.get(5, TimeUnit.SECONDS);
    }

    private ArgumentCaptor<HttpRequest> requests(final int expected) throws Exception {
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(expected)).send(requests.capture(), any());
        return requests;
    }

    private void assertGeneric(final Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(e.getReason()).isEqualTo(EntraUserClient.GENERIC_FAILURE).doesNotContain(GRAPH_DETAIL);
        });
    }

    private void create() {
        client.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD);
    }

    // ---------------------------------------------------------------------------------- the request

    @Test
    void creating_a_user_should_post_a_local_account_signed_in_by_email_and_return_its_object_id() throws Exception {
        send(token(), created());

        String objectId = client.createUser("Joe", "Bloggs", "joe.bloggs@example.com", PASSWORD);

        assertThat(objectId).isEqualTo("entra-oid-1");
        HttpRequest post = requests(2).getAllValues().get(1);
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.uri().toString()).isEqualTo("https://graph.microsoft.com/v1.0/users");
        assertThat(post.headers().firstValue("Authorization")).contains("Bearer graph-token");
        JsonNode body = new ObjectMapper().readTree(bodyOf(post));
        assertThat(body.get("accountEnabled").asBoolean()).isTrue();
        assertThat(body.get("displayName").asText()).isEqualTo("Joe Bloggs");
        assertThat(body.get("givenName").asText()).isEqualTo("Joe");
        assertThat(body.get("surname").asText()).isEqualTo("Bloggs");
        JsonNode identity = body.get("identities").get(0);
        assertThat(identity.get("signInType").asText()).isEqualTo("emailAddress");
        assertThat(identity.get("issuer").asText()).isEqualTo("tenant.onmicrosoft.com");
        assertThat(identity.get("issuerAssignedId").asText()).isEqualTo("joe.bloggs@example.com");
        assertThat(body.get("passwordProfile").get("password").asText()).isEqualTo(PASSWORD);
        assertThat(body.get("passwordProfile").get("forceChangePasswordNextSignIn").asBoolean()).isFalse();
    }

    @Test
    void name_with_quotes_should_not_break_the_json() throws Exception {
        send(token(), created());

        client.createUser("Joe \"Jo\"", "O'Neil", "joe@example.com", PASSWORD);

        JsonNode body = new ObjectMapper().readTree(bodyOf(requests(2).getAllValues().get(1)));
        assertThat(body.get("displayName").asText()).isEqualTo("Joe \"Jo\" O'Neil");
    }

    @Test
    void the_token_request_should_use_the_user_credential_and_the_graph_scope() throws Exception {
        send(token(), created());

        create();

        HttpRequest tokenRequest = requests(2).getAllValues().get(0);
        assertThat(tokenRequest.uri().toString())
            .isEqualTo("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token");
        assertThat(bodyOf(tokenRequest)).isEqualTo("grant_type=client_credentials&client_id=user-onboarding-id"
            + "&client_secret=user%2Bsecret%26with%3Dsymbols&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default");
    }

    @Test
    void stand_in_for_graph_and_the_sign_in_endpoint_should_be_used_when_configured() throws Exception {
        ReflectionTestUtils.setField(client, "graphBase", "http://stand-in:8080/graph");
        ReflectionTestUtils.setField(client, "loginBase", "http://stand-in:8080");
        send(token(), created());

        create();

        assertThat(requests(2).getAllValues().get(0).uri().toString())
            .isEqualTo("http://stand-in:8080/tenant-1/oauth2/v2.0/token");
        assertThat(requests(2).getAllValues().get(1).uri().toString()).isEqualTo("http://stand-in:8080/graph/users");
    }

    @Test
    void without_a_configured_credential_nothing_should_be_called_and_the_answer_is_503() throws Exception {
        ReflectionTestUtils.setField(client, "clientSecret", "NOT_SET");

        assertThatThrownBy(this::create).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.deleteUser("entra-oid-1")).isInstanceOf(ResponseStatusException.class);

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    // ----------------------------------------------------------------------------------- refusals

    @Test
    void an_address_entra_already_has_should_be_reported_as_already_existing() throws Exception {
        send(token(), response(400, "{\"error\":{\"code\":\"Request_BadRequest\",\"message\":"
            + "\"Another object with the same value for property identities already exists.\"}}"));

        assertThatThrownBy(this::create).isInstanceOf(EntraUserClient.AlreadyExists.class);
    }

    @Test
    void an_object_conflict_should_also_be_reported_as_already_existing() throws Exception {
        send(token(), response(400, "{\"error\":{\"code\":\"ObjectConflict\"}}"));

        assertThatThrownBy(this::create).isInstanceOf(EntraUserClient.AlreadyExists.class);
    }

    @Test
    void password_that_fails_entras_complexity_rules_should_be_reported_as_rejected() throws Exception {
        send(token(), response(400, "{\"error\":{\"message\":\"The specified password does not comply with "
            + "password complexity requirements.\"}}"));

        assertThatThrownBy(this::create).isInstanceOf(EntraUserClient.PasswordRejected.class);
    }

    @Test
    void any_other_bad_request_should_give_the_generic_message_without_graphs_detail() throws Exception {
        send(token(), response(400, "{\"error\":\"" + GRAPH_DETAIL + "\"}"));

        assertGeneric(this::create);
    }

    @Test
    void forbidden_should_give_the_generic_message_because_the_credential_lacks_permission() throws Exception {
        send(token(), response(403, "{\"error\":{\"code\":\"Authorization_RequestDenied\",\"message\":\""
            + GRAPH_DETAIL + "\"}}"));

        assertGeneric(this::create);
    }

    @Test
    void server_error_should_give_the_generic_message() throws Exception {
        send(token(), response(500, "{\"error\":\"" + GRAPH_DETAIL + "\"}"));

        assertGeneric(this::create);
    }

    @Test
    void created_user_with_no_object_id_should_be_refused_because_it_could_not_be_undone() throws Exception {
        send(token(), response(201, "{}"));

        assertGeneric(this::create);
    }

    @Test
    void timeout_should_give_the_generic_message() throws Exception {
        doThrow(new HttpTimeoutException("timed out")).when(httpClient).send(any(HttpRequest.class), any());

        assertGeneric(this::create);
    }

    @Test
    void an_interrupt_should_give_the_generic_message_and_leave_the_thread_interrupted() throws Exception {
        doThrow(new InterruptedException("shutting down")).when(httpClient).send(any(HttpRequest.class), any());

        try {
            assertGeneric(this::create);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void an_unreadable_response_should_give_the_generic_message() throws Exception {
        send(token(), response(201, "not json"));

        assertGeneric(this::create);
    }

    // -------------------------------------------------------------------------------------- deleting

    @Test
    void deleting_should_delete_the_user_by_object_id_with_no_body() throws Exception {
        send(token(), response(204, ""));

        client.deleteUser("entra-oid-1");

        HttpRequest delete = requests(2).getAllValues().get(1);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.uri().toString()).isEqualTo("https://graph.microsoft.com/v1.0/users/entra-oid-1");
        assertThat(delete.bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    @Test
    void user_that_is_already_gone_should_not_be_a_failure() throws Exception {
        send(token(), response(404, "{}"));

        assertThatCode(() -> client.deleteUser("entra-oid-1")).doesNotThrowAnyException();
    }

    @Test
    void any_other_failure_deleting_should_surface() throws Exception {
        send(token(), response(500, "{}"));

        assertGeneric(() -> client.deleteUser("entra-oid-1"));
    }

    @Test
    void cleaning_up_should_delete_the_user() throws Exception {
        send(token(), response(204, ""));

        client.undoCreate("entra-oid-1");

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void cleaning_up_should_never_throw_so_it_cannot_hide_the_failure_being_handled() throws Exception {
        send(token(), response(500, "{}"));

        assertThatCode(() -> client.undoCreate("entra-oid-1")).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------ mode

    @Test
    void users_should_not_be_created_in_entra_unless_asked_for() {
        assertThat(client.enabled()).isFalse();

        ReflectionTestUtils.setField(client, "mode", "local");
        assertThat(client.enabled()).isFalse();

        ReflectionTestUtils.setField(client, "mode", "  Entra ");
        assertThat(client.enabled()).isTrue();
        assertThatCode(client::requireKnownMode).doesNotThrowAnyException();
    }

    @Test
    void mode_that_is_not_local_or_entra_should_stop_the_service_starting() {
        ReflectionTestUtils.setField(client, "mode", "entr4");

        assertThatThrownBy(client::requireKnownMode).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("entr4");
    }

    @Test
    void missing_mode_should_stop_the_service_starting_rather_than_default_silently() {
        ReflectionTestUtils.setField(client, "mode", null);

        assertThat(client.enabled()).isFalse();
        assertThatThrownBy(client::requireKnownMode).isInstanceOf(IllegalStateException.class);
    }
}
