package uk.gov.hmcts.amp.domain;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueDetailTest {

    private static final String PORTAL = "https://amp-portal.example";

    private static CatalogueDetail detail(final String portalUrl, final String tryItNowApi) {
        final MarketplaceApiEntity api = new MarketplaceApiEntity();
        api.setShortCode("rcc");
        api.setApimApiName("common-platform-api-refdata-court-hearing-court-houses");
        return CatalogueDetail.from(api, "A description.", portalUrl, tryItNowApi, null);
    }

    @Test
    void the_portal_link_should_name_the_try_it_now_api_rather_than_the_real_one() {
        assertThat(detail(PORTAL, "try-it-now-rcc").portalUrl())
            .isEqualTo(PORTAL + "/api-details#api=try-it-now-rcc");
    }

    @Test
    void trailing_slashes_on_the_portal_should_not_double_up() {
        assertThat(detail(PORTAL + "//", "try-it-now-rcc").portalUrl())
            .isEqualTo(PORTAL + "/api-details#api=try-it-now-rcc");
    }

    @Test
    void no_try_it_now_api_should_mean_no_link() {
        assertThat(detail(PORTAL, null).portalUrl()).isNull();
        assertThat(detail(PORTAL, " ").portalUrl()).isNull();
    }

    @Test
    void no_portal_should_mean_no_link() {
        assertThat(detail(null, "try-it-now-rcc").portalUrl()).isNull();
        assertThat(detail("", "try-it-now-rcc").portalUrl()).isNull();
    }
}