package uk.gov.hmcts.amp.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApimPropertiesTest {

    private static ApimProperties withTryItNowApis(final Map<String, String> names) {
        return new ApimProperties("a-tenant", "a-subscription", "rg", "instance",
            "https://amp-portal.example", names);
    }

    @Test
    void configured_try_it_now_api_should_be_returned_by_short_code() {
        assertThat(withTryItNowApis(Map.of("slc", "try-it-now---public-api")).tryItNowApi("slc"))
            .isEqualTo("try-it-now---public-api");
    }

    @Test
    void blank_or_missing_try_it_now_api_should_be_null() {
        final ApimProperties apim = withTryItNowApis(Map.of("rcc", " "));
        assertThat(apim.tryItNowApi("rcc")).isNull();
        assertThat(apim.tryItNowApi("pcd")).isNull();
    }

    @Test
    void no_try_it_now_apis_configured_should_be_null() {
        assertThat(withTryItNowApis(null).tryItNowApi("slc")).isNull();
    }
}