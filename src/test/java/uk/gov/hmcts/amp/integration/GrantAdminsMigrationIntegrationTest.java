package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class GrantAdminsMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V1.032__grant_admins.sql";
    private static final Pattern OBJECT_ID =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startWithNoUsers() {
        applicationApis.deleteAll();
        applications.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void every_object_id_in_the_migration_should_become_admin() throws IOException {
        List<String> adminOids = OBJECT_ID.matcher(migrationSql()).results().map(MatchResult::group).toList();
        assertThat(adminOids).isNotEmpty();
        adminOids.forEach(oid -> save(oid, oid + "@example.com"));

        runMigration();

        assertThat(adminOids).allSatisfy(oid -> assertThat(roleOf(oid)).isEqualTo(UserRole.ADMIN));
    }

    @Test
    void everyone_else_should_stay_a_user() throws IOException {
        String otherOid = UUID.randomUUID().toString();
        save(otherOid, "other@example.com");

        runMigration();

        assertThat(roleOf(otherOid)).isEqualTo(UserRole.USER);
    }

    private static String migrationSql() throws IOException {
        return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
    }

    private void runMigration() throws IOException {
        jdbc.execute(migrationSql());
    }

    private UserRole roleOf(final String oid) {
        return userRepository.findByEntraObjectId(oid).map(UserEntity::getRole).orElseThrow();
    }

    private void save(final String oid, final String email) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 9, 0);
        UserEntity user = new UserEntity();
        user.setEntraObjectId(oid);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setStatus(UserEntity.ACTIVE);
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        userRepository.saveAndFlush(user);
    }
}