package uk.gov.hmcts.amp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/** Entra returns the secret once, on the call that creates it, so every other view of an application omits it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppRegistration(String objectId, String clientId, String displayName, OffsetDateTime created,
                              List<String> roles, String clientSecret, String keyId) {

    public static AppRegistration summary(final String objectId, final String clientId, final String displayName,
                                          final OffsetDateTime created) {
        return new AppRegistration(objectId, clientId, displayName, created, null, null, null);
    }

    public static AppRegistration registered(final String objectId, final String clientId, final String clientSecret,
                                             final String keyId, final List<String> roles) {
        return new AppRegistration(objectId, clientId, null, null, roles, clientSecret, keyId);
    }

    public AppRegistration withRoles(final List<String> granted) {
        return new AppRegistration(objectId, clientId, displayName, created, granted, clientSecret, keyId);
    }
}
