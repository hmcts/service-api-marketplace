package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApplicationRequest {

    @NotBlank(message = "name is required.")
    private String name;

    // The real set is sandbox|production, but only sandbox is wired up today -
    // no production Entra/APIM configuration exists yet (see PR #84's "what's
    // blocking it"). Widen the pattern to sandbox|production once it does.
    @NotBlank(message = "environment is required.")
    @Pattern(regexp = "sandbox", message = "environment must be 'sandbox' - production is not available yet.")
    private String environment;

    @NotEmpty(message = "at least one apiShortCode is required.")
    private List<String> apiShortCodes;
}
