package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.ApiProduct;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final MarketplaceApiRepository apis;
    private final EntraProperties entra;

    @GetMapping
    public List<ApiProduct> listProducts() {
        log.info("List the API catalogue");
        return apis.findAllByOrderByShortCodeAsc().stream()
            .map(api -> ApiProduct.from(api, entra.tenantId(), resourceAppId(api.getShortCode())))
            .toList();
    }

    /** An API with no resource app in this environment is listed without one, not hidden. */
    private String resourceAppId(final String shortCode) {
        Map<String, String> configured = entra.resourceAppIds();
        String appId = configured == null ? null : configured.get(shortCode);
        return appId == null || appId.isBlank() ? null : appId;
    }
}
