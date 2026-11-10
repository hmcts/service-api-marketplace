package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;

/** One API, with everything held about it. */
public record CatalogueDetail(String shortCode, String title, String summary, String team,
                              String domain, String platform, Boolean subscribable,
                              String catalogueName, String description, String docsUrl,
                              String repoUrl, String gatewayPath, String portalUrl,
                              String resourceAppId, String role) {

    private static final String API_DETAILS = "/api-details#api=";

    public static CatalogueDetail from(final MarketplaceApiEntity api, final String description,
                                       final String portalUrl, final String tryItNowApi,
                                       final String resourceAppId) {
        return new CatalogueDetail(api.getShortCode(), api.getTitle(),
            CatalogueEntry.summary(description),
            api.getTeam(), api.getDomain(), api.getPlatform(), CatalogueEntry.subscribable(api),
            api.getCatalogueName(), description, api.getDocsUrl(), api.getRepoUrl(),
            api.getGatewayPath(), portalPage(tryItNowApi, portalUrl),
            resourceAppId, api.getRole());
    }

    /**
     * The Try It Now API's page, where a consumer runs the operations with their sandbox credentials.
     * Absent where either half is unset, rather than a link to the portal's front page.
     */
    private static String portalPage(final String tryItNowApi, final String portalUrl) {
        if (tryItNowApi == null || tryItNowApi.isBlank() || portalUrl == null || portalUrl.isBlank()) {
            return null;
        }
        String base = portalUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + API_DETAILS + tryItNowApi;
    }
}
