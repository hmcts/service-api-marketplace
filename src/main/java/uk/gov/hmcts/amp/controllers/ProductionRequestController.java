package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.ProductionRequestsApi;
import uk.gov.hmcts.amp.openapi.model.ProductionRequest;
import uk.gov.hmcts.amp.openapi.model.ProductionRequestDetail;
import uk.gov.hmcts.amp.services.ProductionRequestService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProductionRequestController implements ProductionRequestsApi {

    private final ProductionRequestService productionRequests;
    private final ResponseMapper mapper;

    @Override
    public List<ProductionRequest> listProductionRequests(final String requestingUserId) {
        log.info("List production requests");
        return mapper.toProductionRequests(productionRequests.list(requestingUserId));
    }

    @Override
    public ProductionRequestDetail getProductionRequest(final String requestingUserId, final String reference) {
        log.info("Read production request {}", reference);
        return mapper.toProductionRequestDetail(productionRequests.get(requestingUserId, reference));
    }

    @Override
    public ProductionRequest createProductionRequest(final String requestingUserId,
                                                            final ProductionAccessRequest request) {
        log.info("Create a production request for application {}", request.clientId());
        return mapper.toProductionRequest(productionRequests.create(requestingUserId, request));
    }

    @Override
    public void deleteProductionRequest(final String requestingUserId, final String reference) {
        log.info("Delete production request {}", reference);
        productionRequests.delete(requestingUserId, reference);
    }
}
