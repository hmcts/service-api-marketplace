package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.ApimProduct;
import uk.gov.hmcts.cp.services.ApimSubscriptionKeyService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/apim/products")
public class ApimProductController {

    private final ApimSubscriptionKeyService subscriptionKeys;

    // Without this there is no way to discover a productId, which createKey requires.
    @GetMapping
    public List<ApimProduct> listProducts() {
        log.info("List APIM products");
        return subscriptionKeys.listProducts();
    }
}
