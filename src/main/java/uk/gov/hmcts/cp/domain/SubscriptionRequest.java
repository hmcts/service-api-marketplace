package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SubscriptionRequest {

    @NotBlank(message = "apiShortCode is required.")
    private String apiShortCode;

    private String api;

    @NotBlank(message = "environment is required.")
    private String environment;

    @NotBlank(message = "expectedVolume is required.")
    private String expectedVolume;

    @NotBlank(message = "useCase is required.")
    @Size(max = 255, message = "useCase must be 255 characters or fewer.")
    private String useCase;

    private boolean oauth2Capable;

    @NotBlank(message = "declaration is required.")
    private String declaration;
}
