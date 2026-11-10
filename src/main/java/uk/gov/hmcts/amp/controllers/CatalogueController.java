package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.ApimProperties;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.CatalogueDetail;
import uk.gov.hmcts.amp.domain.CatalogueEntry;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.CatalogueApi;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;
import uk.gov.hmcts.amp.services.DeveloperPortalClient;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CatalogueController implements CatalogueApi {

    private final MarketplaceApiRepository apis;
    private final ApimProperties apim;
    private final EntraProperties entra;
    private final DeveloperPortalClient developerPortal;
    private final ResponseMapper mapper;

    @Override
    public List<uk.gov.hmcts.amp.openapi.model.CatalogueEntry> listCatalogue() {
        log.info("List the published catalogue");
        return mapper.toCatalogueEntries(apis.findByListedTrueOrderByTitleAsc().stream()
            .map(api -> CatalogueEntry.from(api, description(api)))
            .toList());
    }

    @Override
    public uk.gov.hmcts.amp.openapi.model.CatalogueDetail getCatalogueEntry(final String shortCode) {
        log.info("Read catalogue entry {}", shortCode);
        final MarketplaceApiEntity api = listedApi(shortCode);
        return mapper.toCatalogueDetail(
            CatalogueDetail.from(api, description(api), apim.portalUrl(), apim.tryItNowApi(shortCode),
                resourceAppId(shortCode)));
    }

    private String description(final MarketplaceApiEntity api) {
        return developerPortal.description(api.getApimApiName());
    }

    /** An API with no resource app in this environment is listed without one, not hidden. */
    private String resourceAppId(final String shortCode) {
        final Map<String, String> configured = entra.resourceAppIds();
        final String appId = configured == null ? null : configured.get(shortCode);
        return appId == null || appId.isBlank() ? null : appId;
    }

    /** Unlisted and unknown answer the same: neither is published. */
    private MarketplaceApiEntity listedApi(final String shortCode) {
        return apis.findById(shortCode)
            .filter(MarketplaceApiEntity::isListed)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No API with short code " + shortCode + "."));
    }
}
