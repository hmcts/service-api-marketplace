package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Roles are optional here and granted separately. A new service principal is not assignable for
 * minutes, so lets do the role grant separately so app ui can show progress
 */
public record NewAppRegistration(@NotBlank String name, List<String> roles) {

    public NewAppRegistration {
        roles = roles == null ? List.of() : roles;
    }
}
