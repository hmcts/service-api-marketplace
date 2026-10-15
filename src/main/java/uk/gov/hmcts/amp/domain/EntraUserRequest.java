package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EntraUserRequest(
    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Enter a valid email address.")
    String email,
    @Size(max = 100, message = "Names must be 100 characters or fewer.")
    String firstName,
    @Size(max = 100, message = "Names must be 100 characters or fewer.")
    String lastName) {
}
