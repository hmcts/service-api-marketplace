package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Enter your first name.") String firstName,
    @NotBlank(message = "Enter your last name.") String lastName,
    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.") String email,
    String organisation,
    @NotNull(message = "Role must be \"consumer\" or \"producer\".") Role role,
    @NotNull(message = "Password must be at least 12 characters long.")
    @Size(min = 12, message = "Password must be at least 12 characters long.") String password) {
}
