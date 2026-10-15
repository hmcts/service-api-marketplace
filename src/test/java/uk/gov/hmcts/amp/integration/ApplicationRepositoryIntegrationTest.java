package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The entities against the real migration, so a column the two disagree on fails here. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class ApplicationRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 9, 0);

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ApplicationApiRepository applicationApis;

    @Autowired
    private UserRepository users;


    private UserEntity owner;

    @BeforeEach
    void startClean() {
        applicationApis.deleteAll();
        applications.deleteAll();
        users.deleteAll();
        owner = users.save(user("oid-owner", "ada.lovelace@hmcts.net"));
    }

    @Test
    void saved_application_should_read_back_with_every_column_mapped() {
        applications.save(application(owner, "Case Viewer", "case-viewer", "client-1"));

        ApplicationEntity found = applications.findByClientId("client-1").orElseThrow();
        assertThat(found.getName()).isEqualTo("Case Viewer");
        assertThat(found.getAppname()).isEqualTo("case-viewer");
        assertThat(found.getObjectId()).isEqualTo("object-client-1");
        assertThat(found.getSecretHint()).isEqualTo("Uc6");
        assertThat(found.getSecretKeyId()).isEqualTo("key-client-1");
        assertThat(found.getSecretExpiresAt()).isEqualTo(NOW.plusYears(2));
        assertThat(found.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void one_user_should_not_have_two_applications_with_the_same_appname() {
        applications.save(application(owner, "Case Viewer", "case-viewer", "client-a"));

        assertThatThrownBy(() -> applications.saveAndFlush(
            application(owner, "Case Viewer", "case-viewer", "client-b")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void two_users_should_be_able_to_use_the_same_appname() {
        UserEntity other = users.save(user("oid-other", "grace.hopper@hmcts.net"));
        applications.save(application(owner, "Case Viewer", "case-viewer", "client-c"));

        applications.saveAndFlush(application(other, "Case Viewer", "case-viewer", "client-d"));

        // The APIM subscription name carries the user reference, so these cannot collide there.
        assertThat(applications.existsByUserIdAndAppname(other.getId(), "case-viewer")).isTrue();
    }

    @Test
    void an_application_should_list_its_apis() {
        ApplicationEntity app = applications.save(application(owner, "Case Viewer", "case-viewer", "client-e"));
        applicationApis.save(api(app, "slc", "amp-user001-case-viewer-slc"));

        ApplicationApiEntity found = applicationApis.findByApplicationId(app.getId()).getFirst();
        assertThat(found.getShortCode()).isEqualTo("slc");
        assertThat(found.getProductId()).isEqualTo("cp-crime-schedulingandlisting-courtschedule");
        assertThat(found.getSubscriptionName()).isEqualTo("amp-user001-case-viewer-slc");
        assertThat(found.getKeyHint()).isEqualTo("a1b");
    }

    @Test
    void the_same_api_should_not_be_subscribed_twice_by_one_application() {
        ApplicationEntity app = applications.save(application(owner, "Case Viewer", "case-viewer", "client-f"));
        applicationApis.save(api(app, "slc", "amp-user001-case-viewer-slc"));

        assertThatThrownBy(() -> applicationApis.saveAndFlush(
            api(app, "slc", "amp-user001-case-viewer-slc-again")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void an_api_should_be_findable_by_short_code_across_applications() {
        ApplicationEntity one = applications.save(application(owner, "One", "one", "client-g"));
        ApplicationEntity two = applications.save(application(owner, "Two", "two", "client-h"));
        applicationApis.save(api(one, "slc", "amp-user001-one-slc"));
        applicationApis.save(api(two, "slc", "amp-user001-two-slc"));

        // What a publisher withdrawing an API needs to ask.
        assertThat(applicationApis.findByShortCode("slc")).hasSize(2);
    }

    @Test
    void deleting_an_application_should_take_its_apis_with_it() {
        ApplicationEntity app = applications.save(application(owner, "Case Viewer", "case-viewer", "client-i"));
        applicationApis.save(api(app, "slc", "amp-user001-case-viewer-slc"));

        applications.delete(app);
        applications.flush();

        assertThat(applicationApis.findByShortCode("slc")).isEmpty();
    }

    private UserEntity user(final String objectId, final String email) {
        UserEntity entity = new UserEntity();
        entity.setEntraObjectId(objectId);
        entity.setEmail(email);
        entity.setFirstName("Ada");
        entity.setLastName("Lovelace");
        entity.setStatus(UserEntity.ACTIVE);
        entity.setCreatedAt(NOW);
        entity.setLastLoginAt(NOW);
        return entity;
    }

    private ApplicationEntity application(final UserEntity user, final String name,
                                          final String appname, final String clientId) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setUser(user);
        entity.setName(name);
        entity.setAppname(appname);
        entity.setClientId(clientId);
        entity.setObjectId("object-" + clientId);
        entity.setSecretHint("Uc6");
        entity.setSecretKeyId("key-" + clientId);
        entity.setSecretExpiresAt(NOW.plusYears(2));
        entity.setCreatedAt(NOW);
        return entity;
    }

    private ApplicationApiEntity api(final ApplicationEntity application, final String shortCode,
                                     final String subscriptionName) {
        ApplicationApiEntity entity = new ApplicationApiEntity();
        entity.setApplication(application);
        entity.setShortCode(shortCode);
        entity.setProductId("cp-crime-schedulingandlisting-courtschedule");
        entity.setSubscriptionName(subscriptionName);
        entity.setKeyHint("a1b");
        entity.setSubscribedAt(NOW);
        return entity;
    }
}
