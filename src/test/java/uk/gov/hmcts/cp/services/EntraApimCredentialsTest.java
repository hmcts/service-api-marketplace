package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraApimCredentialsTest {

    @Mock
    private EntraAppRegistrationClient entra;

    @Mock
    private ApimSubscriptionClient apim;

    @Mock
    private ApimProductRegistry products;

    private EntraApimCredentials credentials;

    @BeforeEach
    void setUp() {
        credentials = new EntraApimCredentials(entra, apim, products);
    }

    private void mode(final String value) {
        ReflectionTestUtils.setField(credentials, "mode", value);
    }

    private ResponseStatusException failure() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "nope");
    }

    @Test
    void credentials_should_be_the_services_own_unless_entra_is_asked_for() {
        assertThat(credentials.enabled()).isFalse();

        mode("local");
        assertThat(credentials.enabled()).isFalse();

        mode("entra");
        assertThat(credentials.enabled()).isTrue();
    }

    @Test
    void the_mode_should_not_depend_on_case_or_surrounding_spaces() {
        mode("  Entra ");

        assertThat(credentials.enabled()).isTrue();
        assertThatCode(credentials::requireKnownMode).doesNotThrowAnyException();
    }

    @Test
    void mode_that_is_not_local_or_entra_should_stop_the_service_starting() {
        mode("entr4");

        assertThatThrownBy(credentials::requireKnownMode)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("entr4");
    }

    @Test
    void missing_mode_should_stop_the_service_starting_rather_than_default_silently() {
        mode(null);

        assertThat(credentials.enabled()).isFalse();
        assertThatThrownBy(credentials::requireKnownMode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void the_calls_should_go_to_the_matching_client() {
        EntraAppRegistrationClient.Registration registration =
            new EntraAppRegistrationClient.Registration("client-1", "secret", "key-1");
        EntraAppRegistrationClient.Secret secret = new EntraAppRegistrationClient.Secret("key-2", "secret-2");
        ApimSubscriptionClient.Subscription subscription =
            new ApimSubscriptionClient.Subscription("product-1", "sub-key", "name-1");
        when(entra.register("App")).thenReturn(registration);
        when(entra.addSecret("client-1")).thenReturn(secret);
        when(products.productIdFor("hearing-results")).thenReturn("product-1");
        when(apim.createSubscription("App", "product-1")).thenReturn(subscription);

        assertThat(credentials.register("App")).isSameAs(registration);
        assertThat(credentials.addSecret("client-1")).isSameAs(secret);
        assertThat(credentials.productFor("hearing-results")).isEqualTo("product-1");
        assertThat(credentials.subscribe("App", "product-1")).isSameAs(subscription);

        credentials.revokeSecret("client-1", "key-1");
        credentials.unsubscribe("name-1");
        credentials.deleteApplication("client-1");

        verify(entra).removeSecret("client-1", "key-1");
        verify(apim).deleteSubscription("name-1");
        verify(entra).delete("client-1");
    }

    @Test
    void undoing_should_do_the_clean_up() {
        credentials.undoRegistration("client-1");
        credentials.undoSecret("client-1", "key-1");
        credentials.undoSubscription("name-1");

        verify(entra).delete("client-1");
        verify(entra).removeSecret("client-1", "key-1");
        verify(apim).deleteSubscription("name-1");
    }

    @Test
    void undoing_should_never_throw_so_it_cannot_hide_the_failure_being_handled() {
        doThrow(failure()).when(entra).delete("client-1");
        doThrow(failure()).when(entra).removeSecret("client-1", "key-1");
        doThrow(failure()).when(apim).deleteSubscription("name-1");

        assertThatCode(() -> {
            credentials.undoRegistration("client-1");
            credentials.undoSecret("client-1", "key-1");
            credentials.undoSubscription("name-1");
        }).doesNotThrowAnyException();
    }
}
