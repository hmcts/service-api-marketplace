package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class UserRepositoryIntegrationTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String OTHER_OID = "bbbbbbbb-1111-2222-3333-444444444444";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private ApplicationRepository applications;

    @BeforeEach
    void startWithNoUsers() {
        applicationApis.deleteAll();
        applications.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saving_a_user_should_persist_it_with_a_generated_id() {
        UserEntity saved = userRepository.saveAndFlush(user(OID, "joe@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.existsById(saved.getId())).isTrue();
    }

    @Test
    void getting_a_user_by_entra_object_id_should_return_them() {
        userRepository.saveAndFlush(user(OID, "joe@example.com"));

        Optional<UserEntity> found = userRepository.findByEntraObjectId(OID);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("joe@example.com");
        assertThat(found.get().getFirstName()).isEqualTo("Joe");
        assertThat(found.get().getStatus()).isEqualTo(UserEntity.ACTIVE);
    }

    @Test
    void getting_a_user_by_email_should_return_them() {
        userRepository.saveAndFlush(user(OID, "joe@example.com"));

        assertThat(userRepository.findByEmail("joe@example.com")).map(UserEntity::getEntraObjectId).contains(OID);
    }

    @Test
    void getting_an_unknown_user_should_return_empty() {
        assertThat(userRepository.findByEntraObjectId(OID)).isEmpty();
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void saving_a_second_user_with_the_same_email_should_be_rejected() {
        userRepository.saveAndFlush(user(OID, "joe@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(OTHER_OID, "joe@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saving_a_second_user_with_the_same_entra_object_id_should_be_rejected() {
        userRepository.saveAndFlush(user(OID, "joe@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(OID, "joseph@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UserEntity user(final String oid, final String email) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 9, 0);
        UserEntity user = new UserEntity();
        user.setEntraObjectId(oid);
        user.setEmail(email);
        user.setFirstName("Joe");
        user.setLastName("Bloggs");
        user.setStatus(UserEntity.ACTIVE);
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        return user;
    }
}
