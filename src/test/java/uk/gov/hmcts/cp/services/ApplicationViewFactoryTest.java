package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.ApiKeySummary;
import uk.gov.hmcts.cp.domain.ApplicationView;
import uk.gov.hmcts.cp.domain.ConnectedApi;
import uk.gov.hmcts.cp.domain.TeamMemberView;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;
import uk.gov.hmcts.cp.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationViewFactoryTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f8a1c94-6b2e-4d51-9a77-0e1c5b8d4f23");
    private static final LocalDateTime CREATED = LocalDateTime.parse("2026-10-02T10:15:30");

    private final ApplicationViewFactory factory = new ApplicationViewFactory();

    private ApplicationEntity.ApplicationEntityBuilder application() {
        return ApplicationEntity.builder()
            .id(10L)
            .user(UserEntity.builder().id(7).build())
            .publicId(PUBLIC_ID)
            .clientId(PUBLIC_ID.toString())
            .name("Alpha")
            .description("first")
            .environment("sandbox")
            .createdAt(CREATED)
            .customAttributes("{\"team\":\"alpha\"}")
            .connectedApis("[{\"id\":\"api-1\",\"name\":\"API One\"}]");
    }

    @Test
    void an_application_should_be_shown_by_its_public_id_never_the_database_key() {
        ApplicationView view = factory.view(application().build(), ViewerRole.ADMINISTRATOR);

        assertThat(view.id()).isEqualTo(PUBLIC_ID.toString());
        assertThat(view.clientId()).isEqualTo(PUBLIC_ID.toString());
        assertThat(view.name()).isEqualTo("Alpha");
        assertThat(view.description()).isEqualTo("first");
        assertThat(view.environment()).isEqualTo("sandbox");
        assertThat(view.owner().type()).isEqualTo("user");
        assertThat(view.owner().id()).isEqualTo(7);
        assertThat(view.viewerRole()).isEqualTo("administrator");
        assertThat(view.customAttributes()).isEqualTo(Map.of("team", "alpha"));
        assertThat(view.connectedApis()).containsExactly(new ConnectedApi("api-1", "API One"));
    }

    @Test
    void times_should_be_iso_instants_ending_in_z_so_a_browser_does_not_read_them_as_local_time() {
        assertThat(factory.view(application().build(), ViewerRole.OWNER).createdAt())
            .isEqualTo("2026-10-02T10:15:30Z");
    }

    @Test
    void stored_data_that_is_not_valid_json_should_not_take_the_application_down() {
        ApplicationView view = factory.view(
            application().customAttributes("{not json").connectedApis("also not json").build(), ViewerRole.OWNER);

        assertThat(view.customAttributes()).isEmpty();
        assertThat(view.connectedApis()).isEmpty();
        assertThat(view.name()).isEqualTo("Alpha");
    }

    @Test
    void missing_stored_data_should_read_as_empty() {
        assertThat(factory.attributes(null)).isEmpty();
        assertThat(factory.attributes("  ")).isEmpty();
        assertThat(factory.connectedApis(null)).isEmpty();
        assertThat(factory.connectedApis("")).isEmpty();
    }

    @Test
    void what_is_written_should_read_back_the_same() {
        Map<String, String> attributes = Map.of("tier", "gold");
        List<ConnectedApi> apis = List.of(new ConnectedApi("a", "A"), new ConnectedApi("b", "B"));

        assertThat(factory.attributes(factory.write(attributes))).isEqualTo(attributes);
        assertThat(factory.connectedApis(factory.write(apis))).isEqualTo(apis);
    }

    @Test
    void secret_should_be_listed_by_its_preview_only() {
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder()
            .publicId(PUBLIC_ID).keyHash("$2a$12$hash").keyPreview("cdef")
            .createdAt(CREATED).revokedAt(CREATED.plusDays(1)).build();

        ApiKeySummary summary = factory.summary(secret);

        assertThat(summary.id()).isEqualTo(PUBLIC_ID.toString());
        assertThat(summary.preview()).isEqualTo("cdef");
        assertThat(summary.createdAt()).isEqualTo("2026-10-02T10:15:30Z");
        assertThat(summary.revokedAt()).isEqualTo("2026-10-03T10:15:30Z");
        assertThat(summary.toString()).doesNotContain("$2a$12$hash");
    }

    @Test
    void secret_that_is_not_revoked_should_have_no_revoked_time() {
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder()
            .publicId(PUBLIC_ID).keyPreview("cdef").createdAt(CREATED).build();

        assertThat(factory.summary(secret).revokedAt()).isNull();
    }

    @Test
    void team_member_should_be_shown_by_public_id_email_and_role() {
        TeamMemberView view = factory.teamMember(ApplicationTeamMemberEntity.builder()
            .publicId(PUBLIC_ID).email("dan@example.com").role("developer").addedAt(CREATED).build());

        assertThat(view).isEqualTo(new TeamMemberView(PUBLIC_ID.toString(), "dan@example.com", "developer",
            "2026-10-02T10:15:30Z"));
    }
}
