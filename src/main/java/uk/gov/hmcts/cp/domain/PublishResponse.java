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
public class PublishResponse {

    private String reference;
    private String status;
    private String requestingOrgName;
    private String requestingUserName;
    private String requestingUserEmail;
    private String apiName;
    private String owningTeam;
    private String contactEmail;
    private String specUrl;
}
