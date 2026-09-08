package uk.gov.hmcts.cp.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

public class TestContainersInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("marketplace")
        .withUsername("marketplace")
        .withPassword("marketplace");

    static {
        POSTGRES.start();
    }

    @Override
    public void initialize(final ConfigurableApplicationContext context) {
        TestPropertyValues.of(
            "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "spring.datasource.username=" + POSTGRES.getUsername(),
            "spring.datasource.password=" + POSTGRES.getPassword()
        ).applyTo(context.getEnvironment());
    }
}
