package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uk.gov.hmcts.cp.services.ApimProductRegistry;
import uk.gov.hmcts.cp.services.ApimSubscriptionClient;
import uk.gov.hmcts.cp.services.EntraAppRegistrationClient;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(
            AppConfig.class,
            EntraAppRegistrationClient.class,
            ApimSubscriptionClient.class,
            ApimProductRegistry.class);

    @Test
    void the_outbound_clients_should_be_wired_with_the_shared_http_client() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HttpClient.class);
            assertThat(context).hasSingleBean(EntraAppRegistrationClient.class);
            assertThat(context).hasSingleBean(ApimSubscriptionClient.class);
        });
    }

    @Test
    void the_shared_http_client_should_have_a_connect_timeout() {
        runner.run(context ->
            assertThat(context.getBean(HttpClient.class).connectTimeout()).isPresent());
    }

    @Test
    void the_clients_should_start_unconfigured_so_registration_fails_fast_rather_than_calling_azure() {
        runner.run(context -> {
            // No credentials in a bare context: the defaults must leave both clients refusing to run.
            assertThatThrownBy(
                    () -> context.getBean(EntraAppRegistrationClient.class).register("My App"))
                .hasMessageContaining("not configured");
            assertThatThrownBy(
                    () -> context.getBean(ApimSubscriptionClient.class).createSubscription("My App", "product"))
                .hasMessageContaining("not configured");
        });
    }
}
