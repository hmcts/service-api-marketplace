package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.controllers.MarketplaceApi.anonymous;

class ProductFunctionalTest {

    @Test
    void the_catalogue_should_list_each_api_with_what_a_consumer_needs_to_call_it() {
        var response = anonymous().get("/products");
        List<Map<String, Object>> products = response.jsonPath().getList("$");

        assertThat(response.statusCode()).isEqualTo(200);
        // Sorted by short code
        assertThat(response.jsonPath().getList("shortCode")).containsSubsequence("rcc", "slc");
        // Product ids, paths and roles move with migrations and differ by environment,
        // so only their presence is checked
        assertThat(products).allSatisfy(product -> assertThat(product)
            .extractingByKeys("title", "productId", "gatewayPath", "role", "tenantId")
            .allSatisfy(value -> assertThat(value).asString().isNotBlank()));
    }
}
