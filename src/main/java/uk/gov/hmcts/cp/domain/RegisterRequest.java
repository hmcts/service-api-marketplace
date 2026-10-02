package uk.gov.hmcts.cp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Deliberately has no bean-validation annotations: the frontend shows the server's message as it
 * is, and the contract's messages and their order (missing fields, then email, then role, then
 * password) are not what annotation-driven validation would produce. AccountService validates.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString(exclude = "password")
public class RegisterRequest {

    private String firstName;

    private String lastName;

    private String email;

    private String organisation;

    private String role;

    private String password;
}
