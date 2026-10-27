package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.amp.services.ClockService;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
public class AppConfig {

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }
}
