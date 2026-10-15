package uk.gov.hmcts.amp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.hmcts.amp.entity.ApplicationEntity;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(Long id, String clientId, String name, String appname, String secretHint,
                                  LocalDateTime secretExpiresAt, LocalDateTime createdAt, String clientSecret) {

    public static ApplicationResponse from(final ApplicationEntity application) {
        return new ApplicationResponse(application.getId(), application.getClientId(), application.getName(),
            application.getAppname(), application.getSecretHint(), application.getSecretExpiresAt(),
            application.getCreatedAt(), null);
    }

    public ApplicationResponse withSecret(final String secret) {
        return new ApplicationResponse(id, clientId, name, appname, secretHint, secretExpiresAt, createdAt, secret);
    }
}
