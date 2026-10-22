package uk.gov.hmcts.amp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.domain.ProductionAccessResponse;
import uk.gov.hmcts.amp.services.ProductionRequestService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production-requests")
public class ProductionRequestController {

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private final ProductionRequestService productionRequests;

    @GetMapping
    public List<ProductionAccessResponse> listRequests(
        @RequestHeader(REQUESTING_USER_ID) final String requestingUserId) {
        log.info("List production requests");
        return productionRequests.list(requestingUserId);
    }

    @GetMapping("/{reference}")
    public ProductionAccessDetail getRequest(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                             @PathVariable final String reference) {
        log.info("Read production request {}", reference);
        return productionRequests.get(requestingUserId, reference);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionAccessResponse createRequest(
        @RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
        @Valid @RequestBody final ProductionAccessRequest request) {
        log.info("Create a production request for application {}", request.clientId());
        return productionRequests.create(requestingUserId, request);
    }

    @DeleteMapping("/{reference}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequest(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                              @PathVariable final String reference) {
        log.info("Delete production request {}", reference);
        productionRequests.delete(requestingUserId, reference);
    }
}
