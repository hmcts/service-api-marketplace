package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** An application with no role authenticates and can call nothing, so at least one is required. */
public record NewAppRegistration(
    @NotBlank String name,
    @NotEmpty(message = "At least one role is required.") List<String> roles) {
}
