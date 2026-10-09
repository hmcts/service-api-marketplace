package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What the web app knows about someone Entra External ID has just signed in. The email is the one
 * Entra verified; the names are whatever the sign-up flow collected, which may be nothing.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EntraSignInRequest {

    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Enter a valid email address.")
    private String email;

    @Size(max = 100, message = "Names must be 100 characters or fewer.")
    private String firstName;

    @Size(max = 100, message = "Names must be 100 characters or fewer.")
    private String lastName;
}
