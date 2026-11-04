package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;

import java.util.regex.Pattern;

/** One API, without its long text and links. */
public record CatalogueEntry(String shortCode, String title, String summary, String team,
                             String domain, String platform, Boolean subscribable) {

    private static final Pattern BLANK_LINE = Pattern.compile("\\R\\s*\\R");

    public static CatalogueEntry from(final MarketplaceApiEntity api, final String description) {
        return new CatalogueEntry(api.getShortCode(), api.getTitle(), summary(description),
            api.getTeam(), api.getDomain(), api.getPlatform(), subscribable(api));
    }

    /** Specifications open with a sentence saying what the API is for, which is the summary. */
    static String summary(final String description) {
        return description == null ? null : BLANK_LINE.split(description, 2)[0].trim();
    }

    /** False where the API has no APIM product. */
    static boolean subscribable(final MarketplaceApiEntity api) {
        return api.getProductId() != null && !api.getProductId().isBlank();
    }
}
