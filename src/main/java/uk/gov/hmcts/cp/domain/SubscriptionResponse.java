package uk.gov.hmcts.cp.domain;

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
public class SubscriptionResponse {

    private String reference;
    private String status;
    private String requestingOrgName;
    private String requestingUserName;
    private String requestingUserEmail;
    private String apiShortCode;
    private String api;
    private String environment;
    private String expectedVolume;
    private String useCase;
    private boolean oauth2Capable;
    private String declaration;
}
