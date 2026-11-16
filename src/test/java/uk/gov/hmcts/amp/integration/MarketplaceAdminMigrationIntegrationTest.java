package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The marketplace_admin migration against real Postgres: what a grant must look like to be stored. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class MarketplaceAdminMigrationIntegrationTest {

    private static final String OID = "7c1d9f2e-4b58-4a31-9f0c-2e6d8a1b3c44";
    private static final String INSERT = "insert into marketplace_admin (entra_object_id) values (?)";

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.update("delete from marketplace_admin");
    }

    @Test
    void a_grant_should_be_stamped_with_the_time_it_was_made() {
        jdbc.update(INSERT, OID);

        LocalDateTime grantedAt = jdbc.queryForObject(
            "select granted_at from marketplace_admin where entra_object_id = ?", LocalDateTime.class, OID);
        assertThat(grantedAt).isNotNull();
    }

    @Test
    void a_grant_should_not_need_the_person_to_have_signed_in() {
        jdbc.update(INSERT, OID);

        Integer matched = jdbc.queryForObject(
            "select count(*) from marketplace_admin a join marketplace_user u"
                + " on u.entra_object_id = a.entra_object_id", Integer.class);
        assertThat(matched).isZero();
    }

    @Test
    void the_same_person_should_not_be_granted_twice() {
        jdbc.update(INSERT, OID);

        assertThatThrownBy(() -> jdbc.update(INSERT, OID)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void an_upper_case_object_id_should_be_refused_because_the_join_would_never_match_it() {
        assertThatThrownBy(() -> jdbc.update(INSERT, OID.toUpperCase()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anything_but_an_object_id_should_be_refused() {
        assertThatThrownBy(() -> jdbc.update(INSERT, "ada.lovelace@hmcts.net"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
