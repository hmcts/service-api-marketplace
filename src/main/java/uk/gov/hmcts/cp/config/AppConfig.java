package uk.gov.hmcts.cp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
