package uk.gov.hmcts.cp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    @Value("${POSTGRES_HOST:NOT_SET}")
    private String postgresHost;

    @Value("${POSTGRES_PORT:NOT_SET}")
    private String postgresPort;

    @Value("${POSTGRES_DATABASE:NOT_SET}")
    private String postgresDatabase;

    @Value("${POSTGRES_USER:NOT_SET}")
    private String postgresUser;

    @Value("${POSTGRES_PASS:NOT_SET}")
    private String postgresPass;

    @EventListener(ApplicationReadyEvent.class)
    public void logEnvironment() {
        log.info("=== Vault secret diagnostics ===");
        log.info("POSTGRES_HOST     : {}", postgresHost);
        log.info("POSTGRES_PORT     : {}", postgresPort);
        log.info("POSTGRES_DATABASE : {}", postgresDatabase);
        log.info("POSTGRES_USER     : {}", postgresUser);
        log.info("POSTGRES_PASS     : {}", postgresPass.equals("NOT_SET") ? "NOT_SET" : "***SET***");
        log.info("================================");
    }
}
