package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.SubscriptionRepository;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class RepositoryIntegrationTest {

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void getting_org_by_id_should_return_seeded_api_marketplace_org() {
        Optional<OrganisationEntity> org = organisationRepository.findById(1);

        assertThat(org).isPresent();
        assertThat(org.get().getName()).isEqualTo("Api Marketplace");
    }

    @Test
    void getting_user_by_email_should_return_seeded_colin_greenwood() {
        Optional<UserEntity> user = userRepository.findByEmail("colin.greenwood@hmcts.net");

        assertThat(user).isPresent();
        assertThat(user.get().getFirstName()).isEqualTo("Colin");
        assertThat(user.get().getLastName()).isEqualTo("Greenwood");
        assertThat(user.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(user.get().getOrganisation().getName()).isEqualTo("Api Marketplace");
    }

    @Test
    void saving_a_subscription_request_should_persist_to_database() {
        // The reference is the key and the application assigns it, so it is set here rather
        // than read back. Persistable is what stops Spring Data reading an assigned key as a
        // detached entity and merging.
        SubscriptionRequestEntity request = SubscriptionRequestEntity.builder()
            .type("SUBSCRIPTION")
            .reference("AR-2026-TEST01")
            .orgName("Api Marketplace")
            .userName("Colin Greenwood")
            .userEmail("alan@example.com")
            .status("PENDING")
            .submittedAt(LocalDateTime.now())
            .build();

        SubscriptionRequestEntity saved = subscriptionRepository.save(request);

        assertThat(saved.getId()).isEqualTo("AR-2026-TEST01");
        assertThat(subscriptionRepository.existsById("AR-2026-TEST01")).isTrue();
    }

    @Test
    void getting_a_saved_subscription_request_should_return_correct_data() {
        SubscriptionRequestEntity saved = subscriptionRepository.save(
            SubscriptionRequestEntity.builder()
                .type("SUBSCRIPTION")
                .reference("AR-2026-TEST02")
                .orgName("Api Marketplace")
                .userName("Colin Greenwood")
                .userEmail("joe.bloggs@example.com")
                .status("PENDING")
                .submittedAt(LocalDateTime.of(2026, 1, 15, 9, 0))
                .build()
        );

        Optional<SubscriptionRequestEntity> found = subscriptionRepository.findById("AR-2026-TEST02");

        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo("SUBSCRIPTION");
        assertThat(found.get().getReference()).isEqualTo("AR-2026-TEST02");
        assertThat(found.get().getOrgName()).isEqualTo("Api Marketplace");
        assertThat(found.get().getUserEmail()).isEqualTo("joe.bloggs@example.com");
        assertThat(found.get().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getting_a_subscription_request_by_its_reference_key_should_return_correct_data() {
        subscriptionRepository.save(
            SubscriptionRequestEntity.builder()
                .type("SUBSCRIPTION")
                .reference("AR-2026-TEST03")
                .orgName("Api Marketplace")
                .userName("Colin Greenwood")
                .userEmail("by.reference@example.com")
                .status("PENDING")
                .submittedAt(LocalDateTime.of(2026, 2, 3, 11, 30))
                .build()
        );

        Optional<SubscriptionRequestEntity> found = subscriptionRepository.findById("AR-2026-TEST03");

        assertThat(found).isPresent();
        assertThat(found.get().getUserEmail()).isEqualTo("by.reference@example.com");
    }

    @Test
    void getting_a_subscription_request_by_an_unknown_reference_key_should_return_empty() {
        assertThat(subscriptionRepository.findById("AR-2026-NOSUCH")).isEmpty();
    }

    @Test
    void saving_a_second_request_with_an_existing_reference_should_be_rejected() {
        // The reference is an assigned primary key, so Spring Data would otherwise read the
        // second entity as detached and merge - overwriting the first request rather than
        // refusing the insert. Persistable on the entity is what makes this a failed insert,
        // so a generator collision can never quietly take somebody else's row.
        subscriptionRepository.save(newRequest("AR-2026-TEST04", "first@example.com"));

        assertThatThrownBy(() ->
            subscriptionRepository.saveAndFlush(newRequest("AR-2026-TEST04", "second@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(subscriptionRepository.findById("AR-2026-TEST04"))
            .get()
            .extracting(SubscriptionRequestEntity::getUserEmail)
            .isEqualTo("first@example.com");
    }

    @Test
    void saving_a_reference_shaped_like_a_uuid_should_be_rejected_by_the_database() {
        // The reference is the only identifier the API exposes for a request, so writing
        // a primary key into the column - the mistake that would put UUIDs back in front
        // of callers - is refused rather than stored.
        SubscriptionRequestEntity request = SubscriptionRequestEntity.builder()
            .type("SUBSCRIPTION")
            .reference(UUID.randomUUID().toString())
            .orgName("Api Marketplace")
            .userName("Colin Greenwood")
            .userEmail("uuid.reference@example.com")
            .status("PENDING")
            .submittedAt(LocalDateTime.now())
            .build();

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(request))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SubscriptionRequestEntity newRequest(final String reference, final String userEmail) {
        return SubscriptionRequestEntity.builder()
            .type("SUBSCRIPTION")
            .reference(reference)
            .orgName("Api Marketplace")
            .userName("Colin Greenwood")
            .userEmail(userEmail)
            .status("PENDING")
            .submittedAt(LocalDateTime.now())
            .build();
    }
}
