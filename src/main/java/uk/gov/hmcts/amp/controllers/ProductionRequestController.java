package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.audit.AuditAction;
import uk.gov.hmcts.amp.audit.LogAudit;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.services.SignedInCaller;
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
    private final SignedInCaller caller;

    @Override
    public List<ProductionRequest> listProductionRequests() {
        log.info("List production requests");
        return mapper.toProductionRequests(productionRequests.list(caller.objectId()));
    }

    @Override
    public ProductionRequestDetail getProductionRequest(final String reference) {
        log.info("Read production request {}", reference);
        return mapper.toProductionRequestDetail(productionRequests.get(caller.objectId(), reference));
    }

    @Override
    @LogAudit(action = AuditAction.CONSUMER_PRODUCTION_REQUEST_SUBMITTED, target = "#result?.reference")
    public ProductionRequest createProductionRequest(final ProductionAccessRequest request) {
        log.info("Create a production request for application {}", request.clientId());
        return mapper.toProductionRequest(productionRequests.create(caller.objectId(), request));
    }

    @Override
    @LogAudit(action = AuditAction.CONSUMER_PRODUCTION_REQUEST_WITHDRAWN, target = "#reference")
    public void deleteProductionRequest(final String reference) {
        log.info("Delete production request {}", reference);
        productionRequests.delete(caller.objectId(), reference);
    }
}
