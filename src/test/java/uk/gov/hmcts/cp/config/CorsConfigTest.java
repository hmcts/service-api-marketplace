package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    // getCorsConfigurations is protected; this is the only way to read back what was registered.
    private static class InspectableRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> registered() {
            return getCorsConfigurations();
        }
    }

    private Map<String, CorsConfiguration> registeredFor(final String origin) {
        CorsConfig config = new CorsConfig();
        if (origin != null) {
            ReflectionTestUtils.setField(config, "frontendOrigin", origin);
        }
        InspectableRegistry registry = new InspectableRegistry();
        config.addCorsMappings(registry);
        return registry.registered();
    }

    @Test
    void only_the_api_paths_should_be_open_to_a_browser_on_another_origin() {
        // Every other endpoint is left exactly as closed as it was.
        assertThat(registeredFor("https://hmcts.github.io")).containsOnlyKeys("/api/**");
    }

    @Test
    void the_frontend_origin_should_be_allowed_and_nobody_else() {
        CorsConfiguration cors = registeredFor("https://hmcts.github.io").get("/api/**");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://hmcts.github.io");
        assertThat(cors.checkOrigin("https://hmcts.github.io")).isEqualTo("https://hmcts.github.io");
        assertThat(cors.checkOrigin("https://evil.example")).isNull();
        assertThat(cors.checkOrigin("https://hmcts.github.io.evil.example")).isNull();
    }

    @Test
    void more_than_one_origin_should_be_allowed_when_comma_separated() {
        CorsConfiguration cors = registeredFor("https://hmcts.github.io, http://localhost:3100").get("/api/**");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://hmcts.github.io", "http://localhost:3100");
    }

    @Test
    void the_token_travels_in_a_header_so_cookies_should_not_be_allowed() {
        CorsConfiguration cors = registeredFor("https://hmcts.github.io").get("/api/**");

        assertThat(cors.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
        assertThat(cors.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type");
    }

    @Test
    void the_methods_the_frontend_uses_should_be_allowed() {
        CorsConfiguration cors = registeredFor("https://hmcts.github.io").get("/api/**");

        assertThat(cors.getAllowedMethods())
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }
}
