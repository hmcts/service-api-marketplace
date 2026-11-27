package uk.gov.hmcts.amp.integration;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

final class SignedIn {

    private SignedIn() {
    }

    static RequestPostProcessor signedInAs(final String objectId) {
        return jwt().jwt(token -> token.claim("oid", objectId));
    }
}
