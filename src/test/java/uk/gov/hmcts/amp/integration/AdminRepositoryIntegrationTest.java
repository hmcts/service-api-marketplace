package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.entity.AdminEntity;
import uk.gov.hmcts.amp.repository.AdminRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The entity against the real migration, so a column the two disagree on fails here. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class AdminRepositoryIntegrationTest {

    private static final String OID = "7c1d9f2e-4b58-4a31-9f0c-2e6d8a1b3c44";

    @Autowired
    private AdminRepository admins;

    @AfterEach
    void clear() {
        admins.deleteAll();
    }

    @Test
    void grant_should_read_back_with_the_time_the_database_stamped_on_it() {
        admins.saveAndFlush(grant(OID));

        AdminEntity found = admins.findByEntraObjectId(UUID.fromString(OID)).orElseThrow();

        assertThat(found.getEntraObjectId()).isEqualTo(UUID.fromString(OID));
        assertThat(found.getGrantedAt()).isNotNull();
    }

    @Test
    void only_a_granted_object_id_should_be_an_admin() {
        admins.saveAndFlush(grant(OID));

        assertThat(admins.existsByEntraObjectId(UUID.fromString(OID))).isTrue();
        assertThat(admins.existsByEntraObjectId(UUID.randomUUID())).isFalse();
    }

    @Test
    void the_case_of_the_object_id_should_not_matter() {
        admins.saveAndFlush(grant(OID.toUpperCase()));

        assertThat(admins.existsByEntraObjectId(UUID.fromString(OID))).isTrue();
    }

    @Test
    void the_same_person_should_not_be_granted_twice() {
        admins.saveAndFlush(grant(OID));

        assertThatThrownBy(() -> admins.saveAndFlush(grant(OID)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AdminEntity grant(final String oid) {
        AdminEntity admin = new AdminEntity();
        admin.setEntraObjectId(UUID.fromString(oid));
        return admin;
    }
}
