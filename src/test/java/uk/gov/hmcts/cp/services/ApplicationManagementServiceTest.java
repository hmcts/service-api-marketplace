package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApplicationDetailResponse;
import uk.gov.hmcts.cp.domain.ApplicationEnvelope;
import uk.gov.hmcts.cp.domain.ConnectApiRequest;
import uk.gov.hmcts.cp.domain.ConnectedApi;
import uk.gov.hmcts.cp.domain.CreateApplicationRequest;
import uk.gov.hmcts.cp.domain.CreatedApplicationResponse;
import uk.gov.hmcts.cp.domain.NewApiKeyResponse;
import uk.gov.hmcts.cp.domain.UpdateApplicationRequest;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.ApplicationSecretRepository;
import uk.gov.hmcts.cp.repository.UserRepository;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Access;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Caller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationManagementServiceTest {

    private static final String AUTH = "Bearer t";
    private static final String APP = "3f8a1c94-6b2e-4d51-9a77-0e1c5b8d4f23";
    private static final UUID PUBLIC_ID = UUID.fromString(APP);
    private static final String SECRET = "amp_" + "a1b2".repeat(12);
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-02T10:00:00");
    private static final Caller OLIVE = new Caller(1, "olive@example.com");

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationSecretRepository secretRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationAccessService access;

    @Mock
    private PasswordService passwordService;

    @Mock
    private ClientSecretGenerator secretGenerator;

    @Mock
    private ClockService clockService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ApplicationViewFactory views = new ApplicationViewFactory();
    private final UserEntity owner = UserEntity.builder().id(1).email("olive@example.com").build();
    private final ApplicationEntity application = ApplicationEntity.builder()
        .id(10L).user(owner).publicId(PUBLIC_ID).clientId(APP).name("Alpha").environment("sandbox")
        .description("first").createdAt(NOW).customAttributes("{\"team\":\"alpha\"}").connectedApis("[]").build();

    private ApplicationManagementService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationManagementService(applicationRepository, secretRepository, userRepository, access,
            views, passwordService, secretGenerator, clockService, transactionTemplate);
        ReflectionTestUtils.setField(service, "allowedEnvironments", "sandbox");
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-02T10:00:00Z"));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
            invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private void grantedAs(final ViewerRole role) {
        when(access.require(AUTH, APP, role)).thenReturn(new Access(OLIVE, application, role));
    }

    private void assertRejected(final Runnable action, final HttpStatus status, final String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(message);
        });
    }

    private CreateApplicationRequest create(final String name, final String environment) {
        return CreateApplicationRequest.builder().name(name).environment(environment).build();
    }

    private void creationWillSucceed() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(userRepository.findById(1)).thenReturn(Optional.of(owner));
        when(secretGenerator.generate()).thenReturn(SECRET);
        when(passwordService.hash(SECRET)).thenReturn("the-hash");
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(secretRepository.save(any(ApplicationSecretEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------------------------- list

    @Test
    void listing_should_show_each_application_with_the_callers_role_on_it() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(applicationRepository.findAccessibleTo(1, "olive@example.com")).thenReturn(List.of(application));
        when(access.roleOf(application, OLIVE)).thenReturn(Optional.of(ViewerRole.OWNER));

        var response = service.list(AUTH);

        assertThat(response.applications()).hasSize(1);
        assertThat(response.applications().get(0).name()).isEqualTo("Alpha");
        assertThat(response.applications().get(0).viewerRole()).isEqualTo("owner");
    }

    @Test
    void listing_should_be_empty_when_the_caller_has_no_applications() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(applicationRepository.findAccessibleTo(1, "olive@example.com")).thenReturn(List.of());

        assertThat(service.list(AUTH).applications()).isEmpty();
    }

    // ------------------------------------------------------------------------------------ create

    @Test
    void creating_should_save_the_application_and_its_first_secret_and_return_that_secret_once() {
        creationWillSucceed();
        CreateApplicationRequest request = create("  Alpha ", "sandbox").toBuilder().description("first").build();

        CreatedApplicationResponse response = service.create(AUTH, request);

        assertThat(response.apiKey()).isEqualTo(SECRET);
        assertThat(response.application().name()).isEqualTo("Alpha");
        assertThat(response.application().viewerRole()).isEqualTo("owner");
        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        ApplicationEntity entity = saved.getValue();
        assertThat(entity.getUser()).isSameAs(owner);
        assertThat(entity.getName()).isEqualTo("Alpha");
        assertThat(entity.getEnvironment()).isEqualTo("sandbox");
        assertThat(entity.getDescription()).isEqualTo("first");
        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
        assertThat(entity.getPublicId()).isNotNull();
        // With no Entra application behind it, the Client ID is the application's own public id.
        assertThat(entity.getClientId()).isEqualTo(entity.getPublicId().toString());
        assertThat(response.application().id()).isEqualTo(entity.getPublicId().toString());
    }

    @Test
    void only_the_hash_of_the_secret_should_be_stored_with_its_last_four_characters() {
        creationWillSucceed();

        service.create(AUTH, create("Alpha", "sandbox"));

        ArgumentCaptor<ApplicationSecretEntity> saved = ArgumentCaptor.forClass(ApplicationSecretEntity.class);
        verify(secretRepository).save(saved.capture());
        assertThat(saved.getValue().getKeyHash()).isEqualTo("the-hash");
        assertThat(saved.getValue().getKeyPreview()).isEqualTo("a1b2");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().toString()).doesNotContain(SECRET);
        assertThat(saved.getValue().getKeyHash()).isNotEqualTo(SECRET);
    }

    @Test
    void the_secret_should_be_hashed_before_the_transaction_opens() {
        creationWillSucceed();

        service.create(AUTH, create("Alpha", "sandbox"));

        InOrder order = inOrder(passwordService, transactionTemplate);
        order.verify(passwordService).hash(SECRET);
        order.verify(transactionTemplate).execute(any());
    }

    @Test
    void name_and_an_environment_are_required_and_must_be_valid() {
        when(access.caller(AUTH)).thenReturn(OLIVE);

        assertRejected(() -> service.create(AUTH, create(null, "sandbox")), HttpStatus.BAD_REQUEST,
            "Enter an application name.");
        assertRejected(() -> service.create(AUTH, create("   ", "sandbox")), HttpStatus.BAD_REQUEST,
            "Enter an application name.");
        assertRejected(() -> service.create(AUTH, create("x".repeat(201), "sandbox")), HttpStatus.BAD_REQUEST,
            "Application name must be 200 characters or fewer.");
        assertRejected(() -> service.create(AUTH, create("Alpha", null)), HttpStatus.BAD_REQUEST,
            "Select a valid environment.");
        assertRejected(() -> service.create(AUTH, create("Alpha", "moon")), HttpStatus.BAD_REQUEST,
            "Select a valid environment.");
        assertRejected(() -> service.create(AUTH, create("Alpha", "sandbox").toBuilder()
            .description("x".repeat(2001)).build()), HttpStatus.BAD_REQUEST,
            "Description must be 2000 characters or fewer.");
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void an_environment_that_exists_but_is_not_enabled_yet_should_say_so() {
        when(access.caller(AUTH)).thenReturn(OLIVE);

        assertRejected(() -> service.create(AUTH, create("Alpha", "production")), HttpStatus.BAD_REQUEST,
            "The production environment is not available yet.");
        assertRejected(() -> service.create(AUTH, create("Alpha", "development")), HttpStatus.BAD_REQUEST,
            "The development environment is not available yet.");
    }

    @Test
    void enabling_more_environments_should_be_a_configuration_change() {
        ReflectionTestUtils.setField(service, "allowedEnvironments", "sandbox, production");
        creationWillSucceed();

        assertThat(service.create(AUTH, create("Alpha", "production")).application().environment())
            .isEqualTo("production");
    }

    @Test
    void name_already_used_in_that_environment_by_this_owner_should_be_refused() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(applicationRepository.existsByUserIdAndNameIgnoreCaseAndEnvironment(1, "Alpha", "sandbox"))
            .thenReturn(true);

        assertRejected(() -> service.create(AUTH, create("Alpha", "sandbox")), HttpStatus.CONFLICT,
            ApplicationManagementService.DUPLICATE);

        verifyNoInteractions(passwordService, secretGenerator);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void two_creations_racing_for_one_name_should_give_the_loser_the_same_refusal() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(userRepository.findById(1)).thenReturn(Optional.of(owner));
        when(secretGenerator.generate()).thenReturn(SECRET);
        when(passwordService.hash(SECRET)).thenReturn("the-hash");
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertRejected(() -> service.create(AUTH, create("Alpha", "sandbox")), HttpStatus.CONFLICT,
            ApplicationManagementService.DUPLICATE);
        verify(secretRepository, never()).save(any());
    }

    @Test
    void an_account_that_no_longer_exists_should_not_be_able_to_create() {
        when(access.caller(AUTH)).thenReturn(OLIVE);
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertRejected(() -> service.create(AUTH, create("Alpha", "sandbox")), HttpStatus.UNAUTHORIZED,
            "Not signed in.");
    }

    // ------------------------------------------------------------------------------------ detail

    @Test
    void detail_should_list_the_secrets_by_preview_newest_first() {
        grantedAs(ViewerRole.DEVELOPER);
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder().publicId(UUID.randomUUID())
            .keyHash("h").keyPreview("a1b2").createdAt(NOW).build();
        when(secretRepository.findByApplicationOrderByCreatedAtDesc(application)).thenReturn(List.of(secret));

        ApplicationDetailResponse response = service.detail(AUTH, APP);

        assertThat(response.application().name()).isEqualTo("Alpha");
        assertThat(response.application().viewerRole()).isEqualTo("developer");
        assertThat(response.apiKeys()).hasSize(1);
        assertThat(response.apiKeys().get(0).preview()).isEqualTo("a1b2");
    }

    // ------------------------------------------------------------------------------------ update

    private UpdateApplicationRequest update() {
        return UpdateApplicationRequest.builder().build();
    }

    @Test
    void updating_should_change_only_what_was_sent() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationEnvelope response = service.update(AUTH, APP,
            update().toBuilder().description("changed").callbackUrl("https://example.com/cb").build());

        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("changed");
        assertThat(saved.getValue().getCallbackUrl()).isEqualTo("https://example.com/cb");
        assertThat(saved.getValue().getPublicKeyUrl()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("Alpha");
        assertThat(response.application().description()).isEqualTo("changed");
        assertThat(response.application().viewerRole()).isEqualTo("administrator");
    }

    @Test
    void sending_nothing_should_leave_everything_as_it_was() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(AUTH, APP, update());

        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("first");
        assertThat(views.attributes(saved.getValue().getCustomAttributes())).containsEntry("team", "alpha");
    }

    @Test
    void custom_attributes_should_be_merged_into_the_existing_ones() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(AUTH, APP, update().toBuilder().customAttributes(Map.of("tier", "gold")).build());

        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(views.attributes(saved.getValue().getCustomAttributes()))
            .containsEntry("team", "alpha").containsEntry("tier", "gold").hasSize(2);
    }

    @Test
    void an_empty_string_should_clear_a_url_field() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(AUTH, APP, update().toBuilder().publicKeyUrl("").build());

        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getPublicKeyUrl()).isEmpty();
    }

    @Test
    void url_has_to_be_a_web_address_so_a_stored_link_cannot_run_script() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        String message = "Enter a valid URL starting with http:// or https://.";

        assertRejected(() -> service.update(AUTH, APP, update().toBuilder().callbackUrl("javascript:alert(1)").build()),
            HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.update(AUTH, APP,
            update().toBuilder().publicKeyUrl("data:text/html,<script>").build()), HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.update(AUTH, APP, update().toBuilder().callbackUrl("example.com").build()),
            HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.update(AUTH, APP,
            update().toBuilder().callbackUrl("https://example.com/" + "x".repeat(2000)).build()),
            HttpStatus.BAD_REQUEST, message);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void both_http_and_https_addresses_should_be_accepted_whatever_their_case() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(AUTH, APP, update().toBuilder().callbackUrl("HTTP://example.com/cb").build());

        ArgumentCaptor<ApplicationEntity> saved = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getCallbackUrl()).isEqualTo("HTTP://example.com/cb");
    }

    @Test
    void custom_attributes_are_limited_in_number_and_size() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 51; i++) {
            tooMany.put("k" + i, "v");
        }
        String message = "Custom attributes need a name and a value of 200 characters or fewer each, "
            + "and there can be no more than 50.";

        assertRejected(() -> service.update(AUTH, APP, update().toBuilder().customAttributes(tooMany).build()),
            HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.update(AUTH, APP,
            update().toBuilder().customAttributes(Map.of("k", "v".repeat(201))).build()), HttpStatus.BAD_REQUEST,
            message);
        assertRejected(() -> service.update(AUTH, APP,
            update().toBuilder().customAttributes(Map.of(" ", "v")).build()), HttpStatus.BAD_REQUEST, message);
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("k", null);
        assertRejected(() -> service.update(AUTH, APP, update().toBuilder().customAttributes(nullValue).build()),
            HttpStatus.BAD_REQUEST, message);
    }

    @Test
    void the_limit_applies_to_the_merged_total_not_just_what_was_sent() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        Map<String, String> fifty = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            fifty.put("k" + i, "v");
        }
        // The application already holds one attribute ("team"), so fifty more makes fifty-one.
        assertRejected(() -> service.update(AUTH, APP, update().toBuilder().customAttributes(fifty).build()),
            HttpStatus.BAD_REQUEST, "Custom attributes need a name and a value of 200 characters or fewer each, "
                + "and there can be no more than 50.");
    }

    // ------------------------------------------------------------------------------------ delete

    @Test
    void the_owner_deleting_should_delete_the_application() {
        grantedAs(ViewerRole.OWNER);

        service.delete(AUTH, APP);

        verify(applicationRepository).delete(application);
    }

    // ----------------------------------------------------------------------------------- secrets

    @Test
    void new_secret_should_be_returned_once_and_stored_only_as_a_hash() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        when(secretGenerator.generate()).thenReturn(SECRET);
        when(passwordService.hash(SECRET)).thenReturn("the-hash");
        UUID secretId = UUID.randomUUID();
        when(secretRepository.save(any(ApplicationSecretEntity.class)))
            .thenAnswer(invocation -> ((ApplicationSecretEntity) invocation.getArgument(0)).toBuilder()
                .publicId(secretId).build());

        NewApiKeyResponse response = service.newSecret(AUTH, APP);

        assertThat(response.apiKey()).isEqualTo(SECRET);
        assertThat(response.id()).isEqualTo(secretId.toString());
        ArgumentCaptor<ApplicationSecretEntity> saved = ArgumentCaptor.forClass(ApplicationSecretEntity.class);
        verify(secretRepository).save(saved.capture());
        assertThat(saved.getValue().getKeyHash()).isEqualTo("the-hash");
        assertThat(saved.getValue().getKeyPreview()).isEqualTo("a1b2");
        assertThat(saved.getValue().getApplication()).isSameAs(application);
    }

    @Test
    void revoking_a_secret_should_stamp_when_it_stopped_working() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        UUID secretId = UUID.randomUUID();
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder()
            .publicId(secretId).keyPreview("a1b2").build();
        when(secretRepository.findByPublicIdAndApplication(secretId, application)).thenReturn(Optional.of(secret));

        assertThat(service.revokeSecret(AUTH, APP, secretId.toString()).ok()).isTrue();

        ArgumentCaptor<ApplicationSecretEntity> saved = ArgumentCaptor.forClass(ApplicationSecretEntity.class);
        verify(secretRepository).save(saved.capture());
        assertThat(saved.getValue().getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revoking_a_secret_that_is_already_revoked_should_change_nothing() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        UUID secretId = UUID.randomUUID();
        ApplicationSecretEntity secret = ApplicationSecretEntity.builder().publicId(secretId)
            .revokedAt(NOW.minusDays(1)).build();
        when(secretRepository.findByPublicIdAndApplication(secretId, application)).thenReturn(Optional.of(secret));

        assertThat(service.revokeSecret(AUTH, APP, secretId.toString()).ok()).isTrue();

        verify(secretRepository, never()).save(any());
    }

    @Test
    void revoking_a_secret_that_does_not_exist_or_is_not_an_id_should_quietly_succeed() {
        grantedAs(ViewerRole.ADMINISTRATOR);
        UUID unknown = UUID.randomUUID();
        when(secretRepository.findByPublicIdAndApplication(unknown, application)).thenReturn(Optional.empty());

        assertThat(service.revokeSecret(AUTH, APP, unknown.toString()).ok()).isTrue();
        assertThat(service.revokeSecret(AUTH, APP, "not-a-uuid").ok()).isTrue();

        verify(secretRepository, never()).save(any());
    }

    // ------------------------------------------------------------------------- connected APIs

    private ConnectApiRequest api(final String id, final String name) {
        return ConnectApiRequest.builder().id(id).name(name).build();
    }

    @Test
    void connecting_an_api_should_add_it_to_the_application() {
        grantedAs(ViewerRole.DEVELOPER);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationEnvelope response = service.connectApi(AUTH, APP, api("api-1", "API One"));

        assertThat(response.application().connectedApis()).containsExactly(new ConnectedApi("api-1", "API One"));
        assertThat(response.application().viewerRole()).isEqualTo("developer");
    }

    @Test
    void an_api_connected_a_second_time_should_be_refused() {
        grantedAs(ViewerRole.DEVELOPER);
        ApplicationEntity connected = application.toBuilder()
            .connectedApis(views.write(List.of(new ConnectedApi("api-1", "API One")))).build();
        when(access.require(AUTH, APP, ViewerRole.DEVELOPER))
            .thenReturn(new Access(OLIVE, connected, ViewerRole.DEVELOPER));

        assertRejected(() -> service.connectApi(AUTH, APP, api("api-1", "renamed")), HttpStatus.CONFLICT,
            "That API is already connected.");
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void connecting_needs_an_id_and_a_name_of_sensible_length() {
        grantedAs(ViewerRole.DEVELOPER);
        String message = "API id and name are required.";

        assertRejected(() -> service.connectApi(AUTH, APP, api(null, "x")), HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.connectApi(AUTH, APP, api("x", " ")), HttpStatus.BAD_REQUEST, message);
        assertRejected(() -> service.connectApi(AUTH, APP, api("x".repeat(201), "n")), HttpStatus.BAD_REQUEST,
            message);
        assertRejected(() -> service.connectApi(AUTH, APP, api("x", "n".repeat(201))), HttpStatus.BAD_REQUEST,
            message);
    }

    @Test
    void disconnecting_an_api_should_leave_the_others() {
        ApplicationEntity connected = application.toBuilder().connectedApis(views.write(
            List.of(new ConnectedApi("api-1", "One"), new ConnectedApi("api-2", "Two")))).build();
        when(access.require(AUTH, APP, ViewerRole.DEVELOPER))
            .thenReturn(new Access(OLIVE, connected, ViewerRole.DEVELOPER));
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationEnvelope response = service.disconnectApi(AUTH, APP, "api-1");

        assertThat(response.application().connectedApis()).containsExactly(new ConnectedApi("api-2", "Two"));
    }

    @Test
    void disconnecting_an_api_that_was_not_connected_should_change_nothing() {
        grantedAs(ViewerRole.DEVELOPER);
        when(applicationRepository.save(any(ApplicationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.disconnectApi(AUTH, APP, "nope").application().connectedApis()).isEmpty();
    }
}
