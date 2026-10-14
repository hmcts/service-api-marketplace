package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntraPropertiesTest {

    private EntraProperties properties(final String clientId, final String secret) {
        return new EntraProperties("a-tenant", clientId, secret, "an-api", 1, 1, 1, 1);
    }

    @Test
    void a_client_id_and_secret_should_count_as_configured() {
        assertThat(properties("an-onboarding-app", "a-secret").credentialConfigured()).isTrue();
    }

    @Test
    void a_missing_secret_should_not_count_as_configured() {
        assertThat(properties("an-onboarding-app", "NOT_SET").credentialConfigured()).isFalse();
        assertThat(properties("an-onboarding-app", " ").credentialConfigured()).isFalse();
        assertThat(properties("an-onboarding-app", null).credentialConfigured()).isFalse();
    }

    @Test
    void a_missing_client_id_should_not_count_as_configured() {
        // A secret alone would otherwise only fail later, when Entra rejects the sign-in.
        assertThat(properties("NOT_SET", "a-secret").credentialConfigured()).isFalse();
        assertThat(properties(" ", "a-secret").credentialConfigured()).isFalse();
        assertThat(properties(null, "a-secret").credentialConfigured()).isFalse();
    }
}
