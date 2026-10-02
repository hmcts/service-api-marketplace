package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.hmcts.cp.mappers.ApplicationMapper;
import uk.gov.hmcts.cp.repository.ApplicationApiKeyRepository;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;
import uk.gov.hmcts.cp.services.ApimProductRegistry;
import uk.gov.hmcts.cp.services.ApplicationService;
import uk.gov.hmcts.cp.services.ApimSubscriptionClient;
import uk.gov.hmcts.cp.services.EntraAppRegistrationClient;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    @Test
    void the_application_service_should_get_a_transaction_template_from_spring() {
        // ApplicationService writes its rows in one short transaction via TransactionTemplate. The
        // integration tests that would catch a missing bean need Docker, so prove the wiring here
        // using Spring Boot's own transaction auto-configuration.
        runner
            .withUserConfiguration(TransactionAutoConfiguration.class, ApplicationService.class)
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(ApplicationRepository.class, () -> mock(ApplicationRepository.class))
            .withBean(ApplicationApiKeyRepository.class, () -> mock(ApplicationApiKeyRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(ApplicationMapper.class, () -> mock(ApplicationMapper.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TransactionTemplate.class);
                assertThat(context).hasSingleBean(ApplicationService.class);
            });
    }
}
