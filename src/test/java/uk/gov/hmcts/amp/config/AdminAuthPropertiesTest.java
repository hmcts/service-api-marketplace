package uk.gov.hmcts.amp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthPropertiesTest {

    private static final String TENANT = "99999999-8888-7777-6666-555555555555";

    @Test
    void issuer_and_keys_should_come_from_the_tenant_and_the_role_default_to_amp_admin() {
        final AdminAuthProperties admin = new AdminAuthProperties(TENANT, "the-web-app", null, null, null);

        assertThat(admin.issuer()).isEqualTo("https://" + TENANT + ".ciamlogin.com/" + TENANT + "/v2.0");
        assertThat(admin.jwkSetUri())
            .isEqualTo("https://" + TENANT + ".ciamlogin.com/" + TENANT + "/discovery/v2.0/keys");
        assertThat(admin.roleOrDefault()).isEqualTo("amp_admin");
    }

    @Test
    void issuer_and_keys_should_be_the_overrides_when_set() {
        final AdminAuthProperties admin = new AdminAuthProperties(TENANT, "the-web-app", null,
            "http://localhost:4600/mock", "http://localhost:4600/mock/jwks");

        assertThat(admin.issuer()).isEqualTo("http://localhost:4600/mock");
        assertThat(admin.jwkSetUri()).isEqualTo("http://localhost:4600/mock/jwks");
    }

    @Test
    void blank_overrides_should_fall_back_to_the_tenant_issuer_and_keys() {
        final AdminAuthProperties admin = new AdminAuthProperties(TENANT, "the-web-app", null, " ", "");

        assertThat(admin.issuer()).startsWith("https://" + TENANT + ".ciamlogin.com/");
        assertThat(admin.jwkSetUri()).startsWith("https://" + TENANT + ".ciamlogin.com/");
    }

    @Test
    void admin_sign_in_should_be_usable_only_with_both_a_tenant_and_an_audience() {
        assertThat(new AdminAuthProperties(TENANT, "the-web-app", null, null, null).usable()).isTrue();
        assertThat(new AdminAuthProperties("", "the-web-app", null, "http://x", "http://x/jwks").usable()).isFalse();
        assertThat(new AdminAuthProperties(TENANT, null, null, null, null).usable()).isFalse();
    }
}
