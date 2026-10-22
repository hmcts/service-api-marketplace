package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.entity.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalApimSubscriptionKeyServiceTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String NAME = "amp-user001-case-viewer-slc";

    private final UserService users = mock(UserService.class);

    // APIM is never touched: everything is answered from memory.
    private final LocalApimSubscriptionKeyService service = new LocalApimSubscriptionKeyService(null, users, null);

    @BeforeEach
    void signedIn() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        when(users.requireSignedIn(OID)).thenReturn(user);
    }

    @Test
    void creating_should_return_keys_that_reading_again_gives_back() {
        SubscriptionKey created = service.create(OID, NAME, "try-it-now-slc", "Case Viewer");

        assertThat(created.productId()).isEqualTo("try-it-now-slc");
        assertThat(created.primaryKey()).isNotBlank().isNotEqualTo(created.secondaryKey());
        assertThat(service.keyValues(OID, NAME).primaryKey()).isEqualTo(created.primaryKey());
        assertThat(service.list(OID)).extracting(SubscriptionKey::name).containsExactly(NAME);
    }

    @Test
    void creating_outside_the_callers_prefix_should_be_rejected() {
        assertThatThrownBy(() -> service.create(OID, "amp-user002-case-viewer-slc", "try-it-now-slc", "Case Viewer"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creating_the_same_name_twice_should_conflict() {
        service.create(OID, NAME, "try-it-now-slc", "Case Viewer");

        assertThatThrownBy(() -> service.create(OID, NAME, "try-it-now-slc", "Case Viewer"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void someone_elses_subscription_should_read_as_not_found() {
        assertThatThrownBy(() -> service.keyValues(OID, "amp-user002-case-viewer-slc"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(OID, "amp-user002-case-viewer-slc"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleting_should_take_it_out_of_the_list() {
        service.create(OID, NAME, "try-it-now-slc", "Case Viewer");

        service.delete(OID, NAME);

        assertThat(service.list(OID)).isEmpty();
    }
}
