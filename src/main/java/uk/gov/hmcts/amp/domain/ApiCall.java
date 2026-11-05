package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.MarketplaceApiCallEntity;

/** A request a consumer is shown for an API, by its path under the API's gateway path. */
public record ApiCall(String title, String path) {

    public static ApiCall from(final MarketplaceApiCallEntity call) {
        return new ApiCall(call.getTitle(), call.getPath());
    }
}
