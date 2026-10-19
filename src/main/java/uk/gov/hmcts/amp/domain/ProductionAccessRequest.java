package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProductionAccessRequest(
    @NotBlank(message = "Choose which application is going live.")
    String clientId,

    @NotBlank(message = "Enter the organisation's legal name.")
    @Size(max = 255, message = "Organisation must be 255 characters or fewer.")
    String organisation,

    @NotNull(message = "Enter a go live date.")
    LocalDate goLiveDate,

    @Pattern(regexp = "low|medium|high", message = "Call volume must be low, medium or high.")
    String callVolume,

    @Pattern(regexp = "yes|no", message = "Answer yes or no for the DPIA.")
    String dpia,

    @Pattern(regexp = "in-place|in-progress|not-needed",
        message = "DSA must be in-place, in-progress or not-needed.")
    String dsa,

    @NotBlank(message = "Enter a security contact email address.")
    @Email(message = "Enter a valid security contact email address.")
    String securityContact,

    @NotBlank(message = "Describe your use case.")
    @Size(max = 1000, message = "Use case must be 1000 characters or fewer.")
    String useCase,

    @AssertTrue(message = "You must confirm the declaration.")
    boolean declarationConfirmed) {
}
