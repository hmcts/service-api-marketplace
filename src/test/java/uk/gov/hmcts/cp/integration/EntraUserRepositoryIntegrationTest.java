package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class EntraUserRepositoryIntegrationTest {

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity entraUser(final String email, final String oid) {
        OrganisationEntity organisation = organisationRepository.findById(1).orElseThrow();
        return UserEntity.builder()
            .organisation(organisation)
            .firstName("")
            .lastName("")
            .email(email)
            .status("ACTIVE")
            .role("consumer")
            .entraObjectId(oid)
            .build();
    }

    @Test
    void a_user_without_a_password_should_be_saved_and_found_by_entra_object_id() {
        UserEntity saved = userRepository.save(
            entraUser("repo-entra-one@example.com", "11111111-1111-1111-1111-111111111111"));

        assertThat(userRepository.findByEntraObjectId("11111111-1111-1111-1111-111111111111"))
            .get()
            .extracting(UserEntity::getId)
            .isEqualTo(saved.getId());
    }

    @Test
    void two_users_should_not_share_an_entra_object_id() {
        userRepository.save(entraUser("repo-entra-two@example.com", "22222222-2222-2222-2222-222222222222"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
            entraUser("repo-entra-three@example.com", "22222222-2222-2222-2222-222222222222")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
