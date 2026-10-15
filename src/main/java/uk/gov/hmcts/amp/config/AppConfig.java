package uk.gov.hmcts.amp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.amp.services.ClockService;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }
}
