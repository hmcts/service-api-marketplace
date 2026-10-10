package uk.gov.hmcts.cp.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An INSERT from an entity names every column, so a null here would override the column default and
 * fail the NOT NULL. These are what let the older registration path, which builds an application
 * without the newer fields, keep working.
 */
class EntityDefaultsTest {

    @Test
    void an_application_built_without_the_newer_fields_should_get_working_defaults() {
        ApplicationEntity application = ApplicationEntity.builder().name("Alpha").build();

        application.applyDefaults();

        assertThat(application.getPublicId()).isNotNull();
        assertThat(application.getCustomAttributes()).isEqualTo("{}");
        assertThat(application.getConnectedApis()).isEqualTo("[]");
    }

    @Test
    void an_application_that_already_has_them_should_keep_them() {
        UUID id = UUID.randomUUID();
        ApplicationEntity application = ApplicationEntity.builder()
            .publicId(id).customAttributes("{\"a\":\"b\"}").connectedApis("[{\"id\":\"x\",\"name\":\"X\"}]").build();

        application.applyDefaults();

        assertThat(application.getPublicId()).isEqualTo(id);
        assertThat(application.getCustomAttributes()).isEqualTo("{\"a\":\"b\"}");
        assertThat(application.getConnectedApis()).isEqualTo("[{\"id\":\"x\",\"name\":\"X\"}]");
    }

    @Test
    void secret_and_a_team_member_should_each_get_a_public_id() {
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder().build();
        ApplicationTeamMemberEntity member = ApplicationTeamMemberEntity.builder().build();

        secret.applyDefaults();
        member.applyDefaults();

        assertThat(secret.getPublicId()).isNotNull();
        assertThat(member.getPublicId()).isNotNull().isNotEqualTo(secret.getPublicId());
    }

    @Test
    void public_id_that_was_supplied_should_not_be_replaced() {
        UUID id = UUID.randomUUID();
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder().publicId(id).build();
        ApplicationTeamMemberEntity member = ApplicationTeamMemberEntity.builder().publicId(id).build();

        secret.applyDefaults();
        member.applyDefaults();

        assertThat(secret.getPublicId()).isEqualTo(id);
        assertThat(member.getPublicId()).isEqualTo(id);
    }
}
