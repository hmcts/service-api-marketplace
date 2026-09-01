package uk.gov.hmcts.cp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SubscriptionResponse {

    private UUID id;
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
