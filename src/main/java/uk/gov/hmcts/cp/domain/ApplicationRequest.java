package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank(message = "environment is required.")
    private String environment;

    @NotEmpty(message = "at least one apiShortCode is required.")
    private List<String> apiShortCodes;
}
