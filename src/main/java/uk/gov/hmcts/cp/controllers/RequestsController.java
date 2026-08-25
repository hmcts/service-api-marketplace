package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.AccessRequest;
import uk.gov.hmcts.cp.domain.AccessRequestResponse;
import uk.gov.hmcts.cp.domain.OnboardingRequest;
import uk.gov.hmcts.cp.services.MarketplaceRequestService;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RequestsController {

    private final MarketplaceRequestService marketplaceRequestService;

    @PostMapping("/api/requests/access")
    public ResponseEntity<AccessRequestResponse> createAccessRequest(@Valid @RequestBody final AccessRequest request) {
        log.info("Access request received: organisation={} apiName={} environment={}",
            Encode.forJava(request.organisation()),
            Encode.forJava(request.apiName()),
            Encode.forJava(request.environment()));
        final String reference = marketplaceRequestService.saveAccessRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccessRequestResponse(reference));
    }

    @PostMapping("/v1/requests/onboarding")
    public ResponseEntity<Void> createOnboardingRequest(@Valid @RequestBody final OnboardingRequest request) {
        log.info("Onboarding request received: organisation={} apiRequested={} environment={}",
            Encode.forJava(request.organisation()),
            Encode.forJava(request.apiRequested()),
            Encode.forJava(request.environment()));
        marketplaceRequestService.saveOnboardingRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
