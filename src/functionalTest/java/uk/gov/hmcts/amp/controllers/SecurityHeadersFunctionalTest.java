package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFunctionalTest {

    private static final Map<String, String> SECURITY_HEADERS = Map.of(
        "Strict-Transport-Security", "max-age=31536000; includeSubDomains",
        "X-Content-Type-Options", "nosniff",
        "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'",
        "Cross-Origin-Resource-Policy", "same-origin",
        "Permissions-Policy", "camera=(), microphone=(), geolocation=()",
        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate",
        "Pragma", "no-cache"
    );

    private final FunctionalClient http = new FunctionalClient();

    @ParameterizedTest
    @ValueSource(strings = {"/catalogue", "/products"})
    void every_response_should_carry_the_security_headers_including_errors(final String path) throws Exception {
        var headers = http.get(path).headers();

        SECURITY_HEADERS.forEach((name, value) -> assertThat(headers.firstValue(name)).contains(value));
    }
}
