package uk.gov.hmcts.cp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.services.ClockService;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }

    // One shared client for the outbound Entra/APIM calls; a bean so tests can substitute it.
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
