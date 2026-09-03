package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.Email;
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
public class PublishRequest {

    @NotBlank(message = "apiName is required.")
    @Size(max = 255, message = "apiName must be 255 characters or fewer.")
    private String apiName;

    @NotBlank(message = "owningTeam is required.")
    @Size(max = 255, message = "owningTeam must be 255 characters or fewer.")
    private String owningTeam;

    @NotBlank(message = "contactEmail is required.")
    @Email(message = "contactEmail must be a valid email address.")
    private String contactEmail;

    @NotBlank(message = "specUrl is required.")
    @Size(max = 2048, message = "specUrl must be 2048 characters or fewer.")
    private String specUrl;
}
