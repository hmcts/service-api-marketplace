package uk.gov.hmcts.amp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;

/** An API as the marketplace knows it. shortCode is the key everything else hangs off. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProduct(String shortCode, String title, String catalogueName, String productId,
                         String tryItNowProductId, String gatewayPath, String role) {

    public static ApiProduct from(final MarketplaceApiEntity api) {
        return new ApiProduct(api.getShortCode(), api.getTitle(), api.getCatalogueName(),
            api.getProductId(), api.getTryItNowProductId(), api.getGatewayPath(), api.getRole());
    }
}
