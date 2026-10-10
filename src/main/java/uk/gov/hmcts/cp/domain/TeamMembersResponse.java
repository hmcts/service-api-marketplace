package uk.gov.hmcts.cp.domain;

import java.util.List;

public record TeamMembersResponse(String ownerEmail, List<TeamMemberView> teamMembers) {
}
