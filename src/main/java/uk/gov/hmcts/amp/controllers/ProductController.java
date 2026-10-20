package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.ApiProduct;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final MarketplaceApiRepository apis;

    @GetMapping
    public List<ApiProduct> listProducts() {
        log.info("List the API catalogue");
        return apis.findAllByOrderByShortCodeAsc().stream().map(ApiProduct::from).toList();
    }
}
