package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.ApiCall;
import uk.gov.hmcts.amp.domain.ApiProduct;
import uk.gov.hmcts.amp.entity.MarketplaceApiCallEntity;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.ProductsApi;
import uk.gov.hmcts.amp.repository.MarketplaceApiCallRepository;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProductController implements ProductsApi {

    private final MarketplaceApiRepository apis;
    private final MarketplaceApiCallRepository calls;
    private final EntraProperties entra;
    private final ResponseMapper mapper;

    @Override
    public List<uk.gov.hmcts.amp.openapi.model.ApiProduct> listProducts() {
        log.info("List the API catalogue");
        Map<String, List<ApiCall>> callsByApi = calls.findAllByOrderByShortCodeAscPositionAsc().stream()
            .collect(Collectors.groupingBy(MarketplaceApiCallEntity::getShortCode,
                Collectors.mapping(ApiCall::from, Collectors.toList())));
        return mapper.toApiProducts(apis.findAllByOrderByShortCodeAsc().stream()
            .map(api -> ApiProduct.from(api, entra.tenantId(), resourceAppId(api.getShortCode()),
                callsByApi.getOrDefault(api.getShortCode(), List.of())))
            .toList());
    }

    /** An API with no resource app in this environment is listed without one, not hidden. */
    private String resourceAppId(final String shortCode) {
        Map<String, String> configured = entra.resourceAppIds();
        String appId = configured == null ? null : configured.get(shortCode);
        return appId == null || appId.isBlank() ? null : appId;
    }
}
