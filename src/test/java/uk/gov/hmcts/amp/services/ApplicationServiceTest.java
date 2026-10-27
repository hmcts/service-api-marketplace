package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.ApplicationProperties;
import uk.gov.hmcts.amp.config.EntraProperties;
import uk.gov.hmcts.amp.domain.AppRegistration;
import uk.gov.hmcts.amp.domain.ApiGrant;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import java.util.Map;

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
    private static final String TENANT_ID = "d44f885c-4fac-47bf-afde-d7d861ec4d7b";
    private static final String RESOURCE_APP_ID = "99999999-9999-9999-9999-999999999999";

    @Mock
    private ApplicationRepository applications;

    @Mock
    private UserService users;

    @Mock
    private EntraAppRegistrationService registrations;

    @Mock
    private MarketplaceApiRepository apis;

    @Mock
    private ClockService clockService;

    @Spy
    private EntraProperties entra = new EntraProperties(TENANT_ID, "an-onboarding-app", "a-secret",
        Map.of("slc", RESOURCE_APP_ID), 1, 1);

    @Spy
    private ApplicationProperties limits = new ApplicationProperties(5);

    @InjectMocks
    private ApplicationService service;

    @Test
    void listing_should_return_only_what_the_caller_owns() {
        when(users.requireSignedIn(OID)).thenReturn(signedIn());
        when(applications.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThat(service.list(OID)).isEmpty();
    }

    @Test
    void reading_your_own_application_should_return_it_without_the_secret() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));

        assertThat(service.get(OID, CLIENT_ID).clientSecret()).isNull();
        assertThat(service.get(OID, CLIENT_ID).appname()).isEqualTo("case-viewer");
    }

    @Test
    void listing_and_reading_should_carry_the_tenant_the_application_is_registered_in() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(owned(user)));
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));

        assertThat(service.list(OID)).singleElement().extracting("tenantId").isEqualTo(TENANT_ID);
        assertThat(service.get(OID, CLIENT_ID).tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void creating_should_carry_the_tenant_alongside_the_secret() {
        givenCreateReaches(registered("the-secret", null));
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        assertThat(service.create(OID, new ApplicationRequest("Case Viewer", List.of())).tenantId())
            .isEqualTo(TENANT_ID);
    }

    @Test
    void creating_at_the_limit_should_be_refused_without_reaching_entra() {
        when(users.requireSignedIn(OID)).thenReturn(signedIn());
        when(applications.existsByUserIdAndAppname(1L, "case-viewer")).thenReturn(false);
        when(applications.countByUserId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(OID, new ApplicationRequest("Case Viewer", List.of())))
            .isInstanceOfSatisfying(ResponseStatusException.class, refused -> {
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(refused.getReason()).isEqualTo("You can have up to 5 applications. "
                    + "Delete an application you no longer need before you add another.");
            });

        verify(registrations, never()).register(any(), any());
    }

    @Test
    void creating_one_below_the_limit_should_go_ahead() {
        givenCreateReaches(registered("the-secret", null));
        when(applications.countByUserId(1L)).thenReturn(4L);
        when(applications.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));

        assertThat(service.create(OID, new ApplicationRequest("Case Viewer", List.of())).clientId())
            .isEqualTo(CLIENT_ID);
    }

    @Test
    void the_allowance_should_say_how_many_are_left() {
        when(users.requireSignedIn(OID)).thenReturn(signedIn());
        when(applications.countByUserId(1L)).thenReturn(3L, 5L);

        assertThat(service.allowance(OID)).satisfies(allowance -> {
            assertThat(allowance.maxPerUser()).isEqualTo(5);
            assertThat(allowance.count()).isEqualTo(3);
            assertThat(allowance.canCreate()).isTrue();
        });
        assertThat(service.allowance(OID).canCreate()).isFalse();
    }

    @Test
    void granting_access_should_resolve_the_resource_app_and_role_from_the_short_code() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));
        when(apis.findById("slc")).thenReturn(Optional.of(api("slc", "app.read")));

        service.grantAccess(OID, CLIENT_ID, "slc");

        // The caller names the API; the audience and the role are ours to decide.
        verify(registrations).grantApiAccess(CLIENT_ID, RESOURCE_APP_ID, "app.read");
    }

    @Test
    void granting_access_to_an_api_with_no_resource_app_here_should_not_reach_entra() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));
        when(apis.findById("rcc")).thenReturn(Optional.of(api("rcc", "app.read")));

        assertThatThrownBy(() -> service.grantAccess(OID, CLIENT_ID, "rcc"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(registrations, never()).grantApiAccess(any(), any(), any());
    }

    @Test
    void granting_a_role_on_someone_elses_application_should_not_reach_entra() {
        UserEntity caller = signedIn();
        UserEntity owner = new UserEntity();
        owner.setId(2L);
        when(users.requireSignedIn(OID)).thenReturn(caller);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(owner)));

        assertThatThrownBy(() -> service.grantAccess(OID, CLIENT_ID, "slc"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No application with client id");

        verify(registrations, never()).grantApiAccess(any(), any(), any());
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

        when(apis.findById("slc")).thenReturn(Optional.of(api("slc", "app.read")));

        service.create(OID, new ApplicationRequest("Case Viewer", List.of("slc")));

        verify(registrations).register("amp-user001-case-viewer",
            List.of(new ApiGrant("slc", RESOURCE_APP_ID, "app.read")));
    }

    private static MarketplaceApiEntity api(final String shortCode, final String role) {
        MarketplaceApiEntity entity = new MarketplaceApiEntity();
        entity.setShortCode(shortCode);
        entity.setRole(role);
        return entity;
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
        when(users.requireSignedIn(OID)).thenReturn(signedIn());
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
