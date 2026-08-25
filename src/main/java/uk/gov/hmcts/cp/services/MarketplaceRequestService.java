package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.AccessRequest;
import uk.gov.hmcts.cp.domain.OnboardingRequest;

// Stub implementation: requests are logged and discarded, and the access request reference is a
// fixed value rather than a generated one. Nothing is persisted.
@Slf4j
@Service
public class MarketplaceRequestService {

    private static final String STUB_REFERENCE = "AR-2026-STUB01";

    public String saveAccessRequest(final AccessRequest request) {
        log.info("Access request accepted - returning stub reference {}", STUB_REFERENCE);
        return STUB_REFERENCE;
    }

    public void saveOnboardingRequest(final OnboardingRequest request) {
        log.info("Onboarding request accepted - not stored");
    }
}
