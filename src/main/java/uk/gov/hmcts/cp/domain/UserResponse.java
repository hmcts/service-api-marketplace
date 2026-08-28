package uk.gov.hmcts.cp.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {

    int id;
    String firstName;
    String lastName;
    String email;
    String status;
    String orgName;
}
