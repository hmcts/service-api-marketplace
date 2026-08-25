package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccessRequest(
    @NotBlank(message = "Enter your full name.") String fullName,
    @NotBlank(message = "Enter your organisation.") String organisation,
    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.") String email,
    String jobTitle,
    @NotBlank(message = "Enter the name of the API you need access to.") String apiName,
    @NotBlank(message = "Enter the environment you need access to.") String environment,
    String callVolume,
    @NotBlank(message = "Describe how the API will be used.") String useCase) {
}
