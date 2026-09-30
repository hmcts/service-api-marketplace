package uk.gov.hmcts.cp.domain;

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
public class ApplicationResponse {

    private Long id;

    private String name;

    private String environment;

    private String clientId;

    // Populated only on the response from the POST that created the application - Entra
    // never lets the secret be read back, so there is nothing to return on later requests.
    private String clientSecret;

    private List<ApiCredential> apiCredentials;
}
