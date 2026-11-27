package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every endpoint that acts for a signed-in user requires the requestingUserId header. Each endpoint
 * declares it in the OpenAPI spec, so each is checked here, in one place.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class RequestingUserHeaderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> endpoints() {
        return Stream.of(
            Arguments.of("GET", "/applications", null),
            Arguments.of("GET", "/apim/subscription-keys", null),
            Arguments.of("POST", "/apim/subscription-keys", "{\"clientId\":\"client-1\",\"shortCode\":\"slc\"}"),
            Arguments.of("GET", "/production-requests", null),
            Arguments.of("POST", "/notify-test", null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void missing_header_should_be_refused_with_400_naming_it(
        final String method, final String path, final String body) throws Exception {
        final var request = request(HttpMethod.valueOf(method), path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        mockMvc.perform(request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("requestingUserId is required."));
    }
}
