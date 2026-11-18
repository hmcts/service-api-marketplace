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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class GrantAdminsMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V1.032__grant_admins.sql";
    private static final String ADMIN_OID = "9ff246b4-11c4-47bf-824f-fc0a31db789a";
    private static final String LAST_ADMIN_OID = "47787cdd-9942-4266-845c-1841c5dc5929";
    private static final String OTHER_OID = "cccccccc-1111-2222-3333-444444444444";
    private static final String TEST_SCRIPT_OID = "11111111-2222-3333-4444-555555555555";

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
    void listed_object_ids_should_become_admin() throws IOException {
        save(ADMIN_OID, "first@example.com");
        save(LAST_ADMIN_OID, "last@example.com");

        runMigration();

        assertThat(roleOf(ADMIN_OID)).isEqualTo(UserRole.ADMIN);
        assertThat(roleOf(LAST_ADMIN_OID)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void everyone_else_should_stay_a_user() throws IOException {
        save(OTHER_OID, "other@example.com");
        save(TEST_SCRIPT_OID, "script@example.com");

        runMigration();

        assertThat(roleOf(OTHER_OID)).isEqualTo(UserRole.USER);
        assertThat(roleOf(TEST_SCRIPT_OID)).isEqualTo(UserRole.USER);
    }

    private void runMigration() throws IOException {
        jdbc.execute(new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8));
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