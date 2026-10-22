package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ApplicationRequest(
    @NotBlank(message = "Enter a name for the application.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ]{6,48}[A-Za-z0-9]$",
        message = "Use letters, digits and spaces only, 8 to 50 characters.")
    String name,
    List<String> apis) {

    public ApplicationRequest {
        apis = apis == null ? List.of() : apis;
    }

    public String appname() {
        return name.trim().toLowerCase(java.util.Locale.UK).replaceAll("\\s+", "-");
    }
}
