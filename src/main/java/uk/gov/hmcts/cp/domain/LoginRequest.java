package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LoginRequest {

    @NotBlank(message = "Email and password are required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    /**
     * Required by the contract but not yet verified — see LoginController. Excluded from
     * toString and equals so it cannot reach a log line or an assertion message.
     */
    @NotBlank(message = "Email and password are required.")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;
}
