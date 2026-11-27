package uk.gov.hmcts.amp.functional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.functional.FunctionalClient.REQUESTING_USER_ID;

class ConsumerAccessFunctionalTest {

    private static final String OID = "00000000-0000-0000-0000-0000000f0006";
    private static final String EMAIL = "functional-test-consumer@amp.example.com";
    // Shaped like a JWT, but signed by nobody.
    private static final String FORGED_TOKEN = "Bearer eyJhbGciOiJub25lIn0."
        + "eyJvaWQiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwZjAwMDYifQ.";

    private final FunctionalClient http = new FunctionalClient();

    static Stream<String> consumerPaths() {
        return Stream.of("/applications", "/applications/00000000-0000-0000-0000-0000000ffffe",
            "/apim/subscription-keys", "/apim/subscription-keys/amp-user999-not-mine-slc/values",
            "/production-requests", "/production-requests/PCR-ABCD2345");
    }

    @ParameterizedTest
    @MethodSource("consumerPaths")
    void consumer_path_should_be_refused_without_a_token(final String path) throws Exception {
        assertRefused(http.get(path));
    }

    @ParameterizedTest
    @MethodSource("consumerPaths")
    void requesting_user_header_alone_should_not_open_a_consumer_path(final String path) throws Exception {
        http.signIn(OID, EMAIL);

        assertRefused(http.get(path, REQUESTING_USER_ID, OID));
    }

    @ParameterizedTest
    @MethodSource("consumerPaths")
    void consumer_path_should_be_refused_for_a_token_nobody_signed(final String path) throws Exception {
        assertRefused(http.get(path, "Authorization", FORGED_TOKEN));
    }

    private static void assertRefused(final HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
