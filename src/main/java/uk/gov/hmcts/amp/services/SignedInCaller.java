package uk.gov.hmcts.amp.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SignedInCaller {

    public String objectId() {
        final String objectId = objectIdOrNull();
        if (objectId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again.");
        }
        return objectId;
    }

    public String objectIdOrNull() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString("oid");
        }
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
