package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApimProductRegistryTest {

    private ApimProductRegistry registryWith(final String config) {
        ApimProductRegistry registry = new ApimProductRegistry();
        ReflectionTestUtils.setField(registry, "productMapConfig", config);
        return registry;
    }

    private void assertUnknown(final ApimProductRegistry registry, final String shortCode) {
        assertThatThrownBy(() -> registry.productIdFor(shortCode))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("Unknown apiShortCode: " + shortCode);
            });
    }

    @Test
    void configured_short_code_should_resolve_to_its_product_id() {
        ApimProductRegistry registry = registryWith("SLC:cp-crime-schedulingandlisting,RCC:cp-crime-hearing-results");

        assertThat(registry.productIdFor("SLC")).isEqualTo("cp-crime-schedulingandlisting");
        assertThat(registry.productIdFor("RCC")).isEqualTo("cp-crime-hearing-results");
    }

    @Test
    void lookup_should_ignore_the_case_of_the_short_code() {
        ApimProductRegistry registry = registryWith("SLC:product-slc,rcc:product-rcc");

        assertThat(registry.productIdFor("slc")).isEqualTo("product-slc");
        assertThat(registry.productIdFor("RCC")).isEqualTo("product-rcc");
    }

    @Test
    void whitespace_around_entries_should_be_ignored() {
        ApimProductRegistry registry = registryWith(" SLC : product-slc , RCC : product-rcc ");

        assertThat(registry.productIdFor("SLC")).isEqualTo("product-slc");
        assertThat(registry.productIdFor("RCC")).isEqualTo("product-rcc");
    }

    @Test
    void an_unknown_short_code_should_be_a_400() {
        assertUnknown(registryWith("SLC:product-slc"), "XYZ");
    }

    @Test
    void an_unset_map_should_reject_every_short_code() {
        assertUnknown(registryWith(""), "SLC");
        assertUnknown(registryWith("   "), "SLC");
        assertUnknown(registryWith(null), "SLC");
    }

    @Test
    void malformed_entries_should_be_skipped_without_losing_the_good_ones() {
        ApimProductRegistry registry = registryWith("SLC:product-slc,garbage,:no-code,NOID:,RCC:product-rcc");

        assertThat(registry.productIdFor("SLC")).isEqualTo("product-slc");
        assertThat(registry.productIdFor("RCC")).isEqualTo("product-rcc");
        assertUnknown(registry, "garbage");
        assertUnknown(registry, "NOID");
    }

    @Test
    void product_id_containing_a_colon_should_keep_everything_after_the_first_one() {
        ApimProductRegistry registry = registryWith("SLC:product:v2");

        assertThat(registry.productIdFor("SLC")).isEqualTo("product:v2");
    }
}
