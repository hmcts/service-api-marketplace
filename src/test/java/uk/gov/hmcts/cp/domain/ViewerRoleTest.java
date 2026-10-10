package uk.gov.hmcts.cp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerRoleTest {

    @Test
    void roles_should_be_ranked_developer_then_administrator_then_owner() {
        assertThat(ViewerRole.OWNER.atLeast(ViewerRole.ADMINISTRATOR)).isTrue();
        assertThat(ViewerRole.ADMINISTRATOR.atLeast(ViewerRole.DEVELOPER)).isTrue();
        assertThat(ViewerRole.DEVELOPER.atLeast(ViewerRole.DEVELOPER)).isTrue();
        assertThat(ViewerRole.DEVELOPER.atLeast(ViewerRole.ADMINISTRATOR)).isFalse();
        assertThat(ViewerRole.ADMINISTRATOR.atLeast(ViewerRole.OWNER)).isFalse();
    }

    @Test
    void roles_should_be_written_in_lower_case_for_the_frontend() {
        assertThat(ViewerRole.DEVELOPER.json()).isEqualTo("developer");
        assertThat(ViewerRole.ADMINISTRATOR.json()).isEqualTo("administrator");
        assertThat(ViewerRole.OWNER.json()).isEqualTo("owner");
    }

    @Test
    void only_developer_and_administrator_should_be_team_roles() {
        assertThat(ViewerRole.teamRole("developer")).contains(ViewerRole.DEVELOPER);
        assertThat(ViewerRole.teamRole("administrator")).contains(ViewerRole.ADMINISTRATOR);
        // Owner is whoever created the application; nobody can be invited as one.
        assertThat(ViewerRole.teamRole("owner")).isEmpty();
        assertThat(ViewerRole.teamRole("Developer")).isEmpty();
        assertThat(ViewerRole.teamRole(null)).isEmpty();
    }
}
