package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionRequest(
    String objectId,
    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Enter a valid email address.")
    String email,
    @Size(max = 200, message = "Name must be 200 characters or fewer.")
    String name) {
}
