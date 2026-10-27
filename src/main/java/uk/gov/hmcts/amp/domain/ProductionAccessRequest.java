package uk.gov.hmcts.amp.domain;

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

    @NotBlank(message = "Describe the data request, how you plan to use it and why you need it.")
    @Size(max = 2000, message = "Data request must be 2000 characters or fewer.")
    String dataRequest,

    @NotBlank(message = "Describe the proposed outputs from this data use.")
    @Size(max = 2000, message = "Proposed outputs must be 2000 characters or fewer.")
    String proposedOutputs) {
}
