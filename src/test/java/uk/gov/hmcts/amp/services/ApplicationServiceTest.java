package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AppRegistration;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private ApplicationRepository applications;

    @Mock
    private UserRepository users;

    @Mock
    private EntraAppRegistrationService registrations;

    @Mock
    private ClockService clockService;

    @InjectMocks
    private ApplicationService service;

    @Test
    void missing_requesting_user_id_should_be_rejected_before_any_lookup() {
        assertThatThrownBy(() -> service.list(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing requestingUserId");
    }

    @Test
    void blank_requesting_user_id_should_be_rejected_before_any_lookup() {
        assertThatThrownBy(() -> service.list("   "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing requestingUserId");
    }

    @Test
    void the_object_id_should_be_looked_up_trimmed_and_lowercased() {
        when(users.findByEntraObjectId(OID)).thenReturn(Optional.of(signedIn()));
        when(applications.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThat(service.list("  " + OID.toUpperCase(java.util.Locale.UK) + "  ")).isEmpty();
    }

    @Test
    void reading_your_own_application_should_return_it_without_the_secret() {
        UserEntity user = signedIn();
        when(users.findByEntraObjectId(OID)).thenReturn(Optional.of(user));
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));

        assertThat(service.get(OID, CLIENT_ID).clientSecret()).isNull();
        assertThat(service.get(OID, CLIENT_ID).appname()).isEqualTo("case-viewer");
    }

    @Test
    void granting_a_role_should_check_ownership_before_calling_entra() {
        UserEntity user = signedIn();
        when(users.findByEntraObjectId(OID)).thenReturn(Optional.of(user));
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));

        service.grantRole(OID, CLIENT_ID, "app.read");

        verify(registrations).grantApiAccess(CLIENT_ID, "app.read");
    }

    @Test
    void granting_a_role_on_someone_elses_application_should_not_reach_entra() {
        UserEntity caller = signedIn();
        UserEntity owner = new UserEntity();
        owner.setId(2L);
        when(users.findByEntraObjectId(OID)).thenReturn(Optional.of(caller));
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(owner)));

        assertThatThrownBy(() -> service.grantRole(OID, CLIENT_ID, "app.read"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No application with client id");

        verify(registrations, never()).grantApiAccess(any(), any());
    }

    @Test
    void registration_that_cannot_be_recorded_should_be_taken_back_out_of_entra() {
        givenCreateReaches(registered("the-secret", null));
        RuntimeException couldNotSave = new IllegalStateException("duplicate key");
        when(applications.saveAndFlush(any())).thenThrow(couldNotSave);

        assertThatThrownBy(() -> service.create(OID, new ApplicationRequest("Case Viewer", List.of())))
            .isSameAs(couldNotSave);

        verify(registrations).delete(CLIENT_ID);
    }

    @Test
    void failed_rollback_should_be_suppressed_so_the_original_failure_still_surfaces() {
        givenCreateReaches(registered("the-secret", null));
        RuntimeException couldNotSave = new IllegalStateException("duplicate key");
        RuntimeException couldNotDelete = new IllegalStateException("entra unreachable");
        when(applications.saveAndFlush(any())).thenThrow(couldNotSave);
        doThrow(couldNotDelete).when(registrations).delete(CLIENT_ID);

        assertThatThrownBy(() -> service.create(OID, new ApplicationRequest("Case Viewer", List.of())))
            .isSameAs(couldNotSave)
            .hasSuppressedException(couldNotDelete);
    }

    @Test
    void registration_without_a_secret_should_be_recorded_with_an_empty_hint() {
        givenCreateReaches(registered(null, null));
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        service.create(OID, new ApplicationRequest("Case Viewer", List.of()));

        assertThat(recorded().getSecretHint()).isEmpty();
    }

    @Test
    void an_expiry_from_entra_should_be_recorded_in_preference_to_the_two_year_default() {
        OffsetDateTime fromEntra = OffsetDateTime.parse("2027-01-31T09:30:00Z");
        givenCreateReaches(registered("the-secret", fromEntra));
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        service.create(OID, new ApplicationRequest("Case Viewer", List.of()));

        assertThat(recorded().getSecretExpiresAt()).isEqualTo(LocalDateTime.of(2027, 1, 31, 9, 30));
        assertThat(recorded().getSecretHint()).isEqualTo("the");
    }

    @Test
    void no_expiry_from_entra_should_fall_back_to_two_years_from_now() {
        givenCreateReaches(registered("the-secret", null));
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        service.create(OID, new ApplicationRequest("Case Viewer", List.of()));

        assertThat(recorded().getSecretExpiresAt()).isEqualTo(LocalDateTime.of(2028, 10, 15, 12, 0));
    }

    @Test
    void the_entra_display_name_should_carry_the_user_reference_and_the_appname() {
        givenCreateReaches(registered("the-secret", null));
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        service.create(OID, new ApplicationRequest("Case Viewer", List.of("app.read")));

        verify(registrations).register("amp-user001-case-viewer", List.of("app.read"));
    }

    private UserEntity signedIn() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEntraObjectId(OID);
        return user;
    }

    private ApplicationEntity owned(final UserEntity user) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(10L);
        application.setUser(user);
        application.setClientId(CLIENT_ID);
        application.setName("Case Viewer");
        application.setAppname("case-viewer");
        return application;
    }

    private AppRegistration registered(final String secret, final OffsetDateTime expiresAt) {
        return AppRegistration.registered("object-1", CLIENT_ID, secret, "key-1", List.of(), expiresAt);
    }

    private void givenCreateReaches(final AppRegistration registration) {
        when(users.findByEntraObjectId(OID)).thenReturn(Optional.of(signedIn()));
        when(applications.existsByUserIdAndAppname(1L, "case-viewer")).thenReturn(false);
        when(clockService.now()).thenReturn(NOW);
        when(registrations.register(any(), any())).thenReturn(registration);
    }

    private ApplicationEntity recorded() {
        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applications).saveAndFlush(saved.capture());
        return saved.getValue();
    }
}
