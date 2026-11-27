package uk.gov.hmcts.amp.functional;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.functional.FunctionalClient.json;

class CatalogueApiFunctionalTest {

    private static final String GUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final FunctionalClient http = new FunctionalClient();

    @Test
    void the_list_should_carry_every_api_ordered_by_title_with_a_summary() throws Exception {
        var response = http.get("/catalogue");

        assertThat(response.statusCode()).isEqualTo(200);
        final List<String> titles = new ArrayList<>();
        final List<String> shortCodes = new ArrayList<>();
        for (JsonNode api : json(response)) {
            titles.add(api.path("title").asText());
            shortCodes.add(api.path("shortCode").asText());
            assertThat(api.path("summary").asText()).isNotBlank();
        }
        assertThat(shortCodes).contains("dl", "pcd", "rcc", "slc");
        assertThat(titles).isSorted();
    }

    @Test
    void the_list_should_leave_out_what_the_detail_page_is_for() throws Exception {
        for (JsonNode api : json(http.get("/catalogue"))) {
            assertThat(api.has("description")).isFalse();
            assertThat(api.has("portalUrl")).isFalse();
            assertThat(api.has("resourceAppId")).isFalse();
            assertThat(api.has("role")).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"slc", "rcc", "pcd", "dl"})
    void each_api_should_carry_what_a_consumer_asks_for_a_token_on(final String shortCode) throws Exception {
        var api = json(http.get("/catalogue/" + shortCode));

        assertThat(api.path("role").asText()).isEqualTo("app.read");
        assertThat(api.path("resourceAppId").asText()).matches(GUID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slc", "rcc", "pcd", "dl"})
    void each_api_should_link_to_its_try_it_now_page_in_the_developer_portal(final String shortCode)
        throws Exception {
        var api = json(http.get("/catalogue/" + shortCode));

        assertThat(api.path("portalUrl").asText())
            .startsWith("https://")
            .endsWith("/api-details#api=try-it-now-" + shortCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slc", "rcc", "pcd", "dl"})
    void each_api_should_have_a_description_even_when_the_portal_cannot_be_read(final String shortCode)
        throws Exception {
        var response = http.get("/catalogue/" + shortCode);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("description").asText()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pcd", "dl"})
    void the_newer_case_administration_apis_should_be_published_on_try_it_now(final String shortCode)
        throws Exception {
        var api = json(http.get("/catalogue/" + shortCode));

        assertThat(api.path("gatewayPath").asText()).isEqualTo("amp/try/" + shortCode);
        assertThat(api.path("domain").asText()).isEqualTo("Case administration");
        assertThat(api.path("platform").asText()).isEqualTo("Common Platform");
        assertThat(api.path("subscribable").asBoolean()).isTrue();
    }

    @Test
    void an_api_nobody_publishes_should_be_not_found() throws Exception {
        var response = http.get("/catalogue/nonsense");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).contains("nonsense");
    }

    @Test
    void products_should_be_gone_now_the_catalogue_carries_what_it_held() throws Exception {
        var response = http.get("/products");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("error").asText()).isEqualTo("Not found.");
    }
}
