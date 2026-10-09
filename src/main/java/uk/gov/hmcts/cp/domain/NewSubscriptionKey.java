package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Azure's rule for a subscription name: lower case letters, digits and hyphens. */
public record NewSubscriptionKey(
    @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,78}[a-z0-9]",
        message = "Name must be lower case letters, digits and hyphens, 2 to 80 characters.")
    String name,
    @NotBlank String productId,
    @NotBlank String displayName) {
}
