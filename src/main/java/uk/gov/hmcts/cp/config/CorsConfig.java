package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Lets the frontend, which is served from GitHub Pages and so is a different origin, call /api from
 * the browser. Only /api: the other endpoints are not meant to be called from a page, and are left
 * exactly as closed as they were. No credentials mode - the token travels in the Authorization
 * header, not a cookie, which is also what keeps this working where third-party cookies are blocked.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // An origin is scheme and host only - no path - so the whole of github.io/hmcts-api-marketplace
    // is "https://hmcts.github.io". Comma-separate to allow more, e.g. a local frontend as well.
    @Value("${FRONTEND_ORIGIN:https://hmcts.github.io}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(Arrays.stream(frontendOrigin.split(",")).map(String::trim).toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type")
            .maxAge(3600);
    }
}
