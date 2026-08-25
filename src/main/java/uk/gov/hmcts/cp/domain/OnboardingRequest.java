package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OnboardingRequest(
    @NotBlank(message = "Enter your full name.") String name,
    @NotBlank(message = "Enter your organisation.") String organisation,
    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.") String email,
    @NotBlank(message = "Enter your job title.") String jobTitle,
    String phone,
    @NotBlank(message = "Enter the name of the API you are requesting.") String apiRequested,
    @NotBlank(message = "Enter the environment you need access to.") String environment,
    String callVolume,
    @NotBlank(message = "Describe how the API will be used.") String useCase) {
}
