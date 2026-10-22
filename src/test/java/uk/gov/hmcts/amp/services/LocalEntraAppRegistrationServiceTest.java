package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AppRegistration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEntraAppRegistrationServiceTest {

    // Graph and the Entra config are never touched: everything is answered from memory.
    private final LocalEntraAppRegistrationService service = new LocalEntraAppRegistrationService(null, null);

    @Test
    void registering_should_come_back_with_made_up_credentials() {
        AppRegistration registered = service.register("amp-user001-case-viewer", List.of("app.read"));

        assertThat(registered.clientId()).isNotBlank();
        assertThat(registered.objectId()).isNotBlank();
        assertThat(registered.clientSecret()).startsWith("local~");
        assertThat(registered.secretExpiresAt()).isNotNull();
        assertThat(registered.roles()).containsExactly("app.read");
    }

    @Test
    void reading_one_back_should_carry_the_roles_granted_since() {
        String clientId = service.register("amp-user001-case-viewer", List.of()).clientId();

        service.grantApiAccess(clientId, "app.read");

        AppRegistration found = service.get(clientId);
        assertThat(found.displayName()).isEqualTo("amp-user001-case-viewer");
        assertThat(found.roles()).containsExactly("app.read");
    }

    @Test
    void listing_should_return_every_registration_it_holds() {
        String first = service.register("amp-user001-first", List.of()).clientId();
        String second = service.register("amp-user001-second", List.of()).clientId();

        // In any order: two made in the same instant have equal created times.
        assertThat(service.list()).extracting(AppRegistration::clientId).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void reading_one_it_never_issued_should_be_not_found() {
        assertThatThrownBy(() -> service.get("never-issued"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleting_should_forget_it_and_deleting_again_should_not_fail() {
        String clientId = service.register("amp-user001-case-viewer", List.of()).clientId();

        service.delete(clientId);
        service.delete(clientId);

        assertThat(service.list()).isEmpty();
    }
}
