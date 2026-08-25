package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.AccessRequest;
import uk.gov.hmcts.cp.domain.AccessRequestResponse;
import uk.gov.hmcts.cp.domain.OnboardingRequest;
import uk.gov.hmcts.cp.services.MarketplaceRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;

@ExtendWith(MockitoExtension.class)
class RequestsControllerTest {

    @Mock
    private MarketplaceRequestService marketplaceRequestService;

    @InjectMocks
    private RequestsController controller;

    @Test
    void creating_an_access_request_should_return_201_with_the_generated_reference() {
        when(marketplaceRequestService.saveAccessRequest(any())).thenReturn("AR-2026-K4M9XZ");

        final ResponseEntity<AccessRequestResponse> response = controller.createAccessRequest(anAccessRequest());

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().reference()).isEqualTo("AR-2026-K4M9XZ");
    }

    @Test
    void creating_an_onboarding_request_should_return_201() {
        final ResponseEntity<Void> response = controller.createOnboardingRequest(anOnboardingRequest());

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private AccessRequest anAccessRequest() {
        return new AccessRequest("Test Requester", "HMCTS", "requester@example.com", "Software Engineer",
            "RAG Service API", "sandbox", "1000/day", "Automating hearing result document distribution");
    }

    private OnboardingRequest anOnboardingRequest() {
        return new OnboardingRequest("Test Requester", "HMCTS", "requester@example.com", "Software Engineer",
            "+447700900000", "RAG Service API", "sandbox", "1000/day",
            "Automating hearing result document distribution");
    }
}
