package uk.gov.hmcts.amp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StartupLoggerTest {

    private static final String KEY = "amptest-00000000-0000-0000-0000-000000000000"
        + "-11111111-1111-1111-1111-111111111111";

    private static final String PORTAL = "https://amp-portal.example";

    @Test
    void the_database_line_should_name_the_url_and_user_but_never_the_password() {
        assertThat(logger().databaseSettings())
            .isEqualTo("jdbc:postgresql://localhost:5432/marketplace as postgres, password set")
            .doesNotContain("s3cret");
    }

    @Test
    void the_api_management_line_should_name_the_instance_it_will_talk_to() {
        assertThat(logger().apimSettings())
            .isEqualTo("sps-api-mgmt-sbox in rg-sps-platform-sbox, subscription a-subscription, "
                + "tenant a-tenant");
    }

    @Test
    void the_entra_line_should_name_the_resource_app_per_api_but_never_the_secret() {
        assertThat(logger().entraSettings())
            .isEqualTo("tenant entra-tenant, onboarding an-onboarding-client, "
                + "onboarding secret set, resource apps {rcc=rcc-app, slc=slc-app}")
            .doesNotContain("an-onboarding-secret");
    }

    @Test
    void an_api_with_no_resource_app_here_should_be_visible_as_missing() {
        StartupLogger partial = new StartupLogger(environment(),
            new ApimProperties("a-tenant", "a-subscription", "rg", "instance", PORTAL, Map.of()),
            new EntraProperties("entra-tenant", "a-client", "a-secret", Map.of(), 1, 1),
            new NotifyProperties(KEY, "https://notify.example", List.of(), List.of(), "PROD"));

        assertThat(partial.entraSettings()).endsWith("resource apps {}");
    }

    @Test
    void the_notify_line_should_mask_every_reviewer_and_never_print_the_key() {
        assertThat(logger().notifySettings())
            .isEqualTo("api key set, base url https://notify.example, environment PROD, "
                + "review emails to [D@justice.gov.uk], team emails to [A@hmcts.net]")
            .doesNotContain(KEY)
            .doesNotContain("DataAccessPanel")
            .doesNotContain("APIMarketplace");
    }

    @Test
    void an_unset_secret_should_be_said_out_loud_rather_than_left_blank() {
        StartupLogger unconfigured = new StartupLogger(environment(),
            new ApimProperties("a-tenant", "a-subscription", "rg", "instance", PORTAL, Map.of()),
            new EntraProperties("entra-tenant", "a-client", "", Map.of("slc", "an-api"), 1, 1),
            new NotifyProperties("", "https://notify.example", List.of(), List.of(), "PROD"));

        assertThat(unconfigured.entraSettings()).contains("onboarding secret NOT SET");
        assertThat(unconfigured.notifySettings()).contains("api key NOT SET")
            .contains("review emails to []");
    }

    @Test
    void the_placeholder_value_should_count_as_unset_rather_than_as_a_secret() {
        // A vault that is mounted but empty resolves to NOT_SET, which is not a secret.
        StartupLogger placeholder = new StartupLogger(environment(),
            new ApimProperties("a-tenant", "a-subscription", "rg", "instance", PORTAL, Map.of()),
            new EntraProperties("entra-tenant", "a-client", "NOT_SET", Map.of("slc", "an-api"), 1, 1),
            new NotifyProperties(KEY, "https://notify.example", List.of(), List.of(), "PROD"));

        assertThat(placeholder.entraSettings()).contains("onboarding secret NOT SET");
    }

    private StartupLogger logger() {
        return new StartupLogger(environment(),
            new ApimProperties("a-tenant", "a-subscription", "rg-sps-platform-sbox",
                "sps-api-mgmt-sbox", PORTAL, Map.of()),
            new EntraProperties("entra-tenant", "an-onboarding-client", "an-onboarding-secret",
                Map.of("slc", "slc-app", "rcc", "rcc-app"), 20, 500),
            new NotifyProperties(KEY, "https://notify.example",
                List.of("DataAccessPanel@justice.gov.uk"),
                List.of("APIMarketplace@hmcts.net"), "PROD"));
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/marketplace")
            .withProperty("spring.datasource.username", "postgres")
            .withProperty("spring.datasource.password", "s3cret");
    }
}
