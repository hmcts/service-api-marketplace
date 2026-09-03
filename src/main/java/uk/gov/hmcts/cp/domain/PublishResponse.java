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
public class PublishResponse {

    private UUID id;
    private String status;
    private String requestingOrgName;
    private String requestingUserName;
    private String requestingUserEmail;
    private String apiName;
    private String owningTeam;
    private String contactEmail;
    private String specUrl;
}
