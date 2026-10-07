package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.ApiSubscription;
import uk.gov.hmcts.cp.domain.ApplicationDetailResponse;
import uk.gov.hmcts.cp.domain.ApplicationEnvelope;
import uk.gov.hmcts.cp.domain.ApplicationListResponse;
import uk.gov.hmcts.cp.domain.ConnectApiRequest;
import uk.gov.hmcts.cp.domain.ConnectedApi;
import uk.gov.hmcts.cp.domain.CreateApplicationRequest;
import uk.gov.hmcts.cp.domain.CreatedApplicationResponse;
import uk.gov.hmcts.cp.domain.NewApiKeyResponse;
import uk.gov.hmcts.cp.domain.OkResponse;
import uk.gov.hmcts.cp.domain.UpdateApplicationRequest;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.ApplicationApiKeyRepository;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.ApplicationSecretRepository;
import uk.gov.hmcts.cp.repository.UserRepository;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Access;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Caller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * "My applications": create, view, change and delete an application, manage its client secrets, and
 * connect it to APIs - the behaviour the frontend was built against on amp-auth. Who may do what is
 * decided in {@link ApplicationAccessService}; each method here names the lowest role it needs.
 *
 * <p>A client secret is shown once: only its bcrypt hash is stored. Where credentials come from is a
 * switch, {@code APPLICATION_CREDENTIALS} (see {@link EntraApimCredentials}). By default the service makes
 * them up itself and needs no external system, so it works wherever the database does. With {@code entra}
 * the Client ID and secrets are a real Entra application's, and each connected API gets a real APIM
 * Subscription Key; taking any of them away takes it away there too, before the row that remembers it.
 * The order throughout is the same: what can be refused is checked first, then the outside systems are
 * asked, and the database is written last, because only the database can be rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationManagementService {

    static final List<String> KNOWN_ENVIRONMENTS = List.of("sandbox", "development", "integration-test", "production");
    static final int MAX_NAME = 200;
    static final int MAX_TEXT = 2000;
    static final int MAX_ATTRIBUTES = 50;
    static final int MAX_ATTRIBUTE_LENGTH = 200;
    static final String DUPLICATE = "You already have an application with this name in this environment. "
        + "Choose a different name, or a different environment to register this one in.";

    private final ApplicationRepository applicationRepository;
    private final ApplicationSecretRepository secretRepository;
    private final UserRepository userRepository;
    private final ApplicationAccessService access;
    private final ApplicationViewFactory views;
    private final PasswordService passwordService;
    private final ClientSecretGenerator secretGenerator;
    private final ClockService clockService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationApiKeyRepository apiKeyRepository;
    private final EntraApimCredentials credentials;

    // Where a client secret came from, and so what undoing it takes: the service's own made-up one, or one
    // Entra issued (which has a key id to revoke it by).
    private record Issued(String clientId, String secret, String entraKeyId, boolean entra) {
    }

    // Which environments may be registered. Production has no Entra or APIM configuration behind it
    // yet, so only sandbox is accepted; add the others here, by configuration, as they are wired up.
    @Value("${APPLICATION_ENVIRONMENTS:sandbox}")
    private String allowedEnvironments;

    public ApplicationListResponse list(final String authorization) {
        Caller caller = access.caller(authorization);
        return new ApplicationListResponse(applicationRepository.findAccessibleTo(caller.id(), caller.email())
            .stream()
            .map(application -> views.view(application,
                access.roleOf(application, caller).orElse(ViewerRole.DEVELOPER)))
            .toList());
    }

    public CreatedApplicationResponse create(final String authorization, final CreateApplicationRequest request) {
        Caller caller = access.caller(authorization);
        String name = validName(request.getName());
        String environment = validEnvironment(request.getEnvironment());
        String description = optionalText(request.getDescription(), "Description");

        if (applicationRepository.existsByUserIdAndNameIgnoreCaseAndEnvironment(caller.id(), name, environment)) {
            throw conflict(DUPLICATE);
        }
        UserEntity owner = userRepository.findById(caller.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in."));

        UUID publicId = UUID.randomUUID();
        // Entra is asked last of the things that can be refused above, and the database last of all: only
        // the database can be rolled back, so what cannot be is not done until the rest is known to be fine.
        Issued issued = issueFirstSecret(name, publicId);
        try {
            // Hashed before the transaction opens: bcrypt is deliberately slow, and a transaction should
            // not hold a connection while it runs.
            String hash = passwordService.hash(issued.secret());
            LocalDateTime now = now();
            ApplicationEntity saved = transactionTemplate.execute(status -> {
                ApplicationEntity application = applicationRepository.save(ApplicationEntity.builder()
                    .user(owner)
                    .name(name)
                    .environment(environment)
                    .description(description)
                    .publicId(publicId)
                    .clientId(issued.clientId())
                    .entraRegistered(issued.entra())
                    .createdAt(now)
                    .build());
                secretRepository.save(secretFor(application, issued.secret(), hash, issued.entraKeyId(), now));
                return application;
            });
            log.info("Application {} created for userId {}", publicId, caller.id());
            return new CreatedApplicationResponse(views.view(saved, ViewerRole.OWNER), issued.secret());
        } catch (DataIntegrityViolationException e) {
            // Two creations of the same name racing past the check above; the unique index decides.
            log.warn("Application creation lost a race for a name");
            undoRegistration(issued);
            throw conflict(DUPLICATE);
        } catch (RuntimeException failure) {
            undoRegistration(issued);
            throw failure;
        }
    }

    // The Client ID and first secret: a real Entra application's, or - as before - the service's own.
    private Issued issueFirstSecret(final String name, final UUID publicId) {
        if (credentials.enabled()) {
            EntraAppRegistrationClient.Registration registration = credentials.register(name);
            return new Issued(registration.clientId(), registration.clientSecret(), registration.keyId(), true);
        }
        return new Issued(publicId.toString(), secretGenerator.generate(), null, false);
    }

    private void undoRegistration(final Issued issued) {
        if (issued.entra()) {
            credentials.undoRegistration(issued.clientId());
        }
    }

    public ApplicationDetailResponse detail(final String authorization, final String applicationId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        return new ApplicationDetailResponse(
            views.view(granted.application(), granted.role()),
            secretRepository.findByApplicationOrderByCreatedAtDesc(granted.application()).stream()
                .map(views::summary)
                .toList(),
            // Every role that can see the application can see its subscription keys: they are what the
            // application is built with. They are not in the list view, only here.
            apiKeyRepository.findByApplicationId(granted.application().getId()).stream()
                .map(key -> new ApiSubscription(key.getApiShortCode(), key.getSubscriptionKey()))
                .toList());
    }

    public ApplicationEnvelope update(final String authorization, final String applicationId,
        final UpdateApplicationRequest request) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        ApplicationEntity current = granted.application();

        Map<String, String> attributes = new LinkedHashMap<>(views.attributes(current.getCustomAttributes()));
        if (request.getCustomAttributes() != null) {
            attributes.putAll(validAttributes(request.getCustomAttributes()));
            if (attributes.size() > MAX_ATTRIBUTES) {
                throw tooManyAttributes();
            }
        }
        ApplicationEntity saved = applicationRepository.save(current.toBuilder()
            .description(request.getDescription() == null
                ? current.getDescription() : optionalText(request.getDescription(), "Description"))
            .publicKeyUrl(request.getPublicKeyUrl() == null
                ? current.getPublicKeyUrl() : validUrl(request.getPublicKeyUrl()))
            .callbackUrl(request.getCallbackUrl() == null
                ? current.getCallbackUrl() : validUrl(request.getCallbackUrl()))
            .customAttributes(views.write(attributes))
            .build());
        return new ApplicationEnvelope(views.view(saved, granted.role()));
    }

    public void delete(final String authorization, final String applicationId) {
        // Owner only, above administrator: it takes the application, and every team member's access
        // to it, away from everyone at once. Secrets and team members go with it (cascade).
        Access granted = access.require(authorization, applicationId, ViewerRole.OWNER);
        ApplicationEntity application = granted.application();
        // What was issued outside is taken back first, and a failure there stops the delete: with the row
        // gone nothing would remember what to take back. Taking back what is already gone is not a failure,
        // so a delete that stopped half-way can simply be tried again.
        for (ApplicationApiKeyEntity key : apiKeyRepository.findByApplicationId(application.getId())) {
            if (key.getSubscriptionName() != null) {
                credentials.unsubscribe(key.getSubscriptionName());
            }
        }
        if (application.isEntraRegistered()) {
            credentials.deleteApplication(application.getClientId());
        }
        applicationRepository.delete(application);
        log.info("Application {} deleted by userId {}", applicationId, granted.caller().id());
    }

    public NewApiKeyResponse newSecret(final String authorization, final String applicationId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        ApplicationEntity application = granted.application();
        Issued issued = issueSecret(application);
        try {
            ApplicationSecretEntity saved = secretRepository.save(secretFor(application, issued.secret(),
                passwordService.hash(issued.secret()), issued.entraKeyId(), now()));
            return new NewApiKeyResponse(saved.getPublicId().toString(), issued.secret());
        } catch (RuntimeException failure) {
            if (issued.entra()) {
                credentials.undoSecret(application.getClientId(), issued.entraKeyId());
            }
            throw failure;
        }
    }

    // A further secret comes from the same place the application's Client ID did.
    private Issued issueSecret(final ApplicationEntity application) {
        if (application.isEntraRegistered()) {
            EntraAppRegistrationClient.Secret secret = credentials.addSecret(application.getClientId());
            return new Issued(application.getClientId(), secret.secretText(), secret.keyId(), true);
        }
        return new Issued(application.getClientId(), secretGenerator.generate(), null, false);
    }

    public OkResponse revokeSecret(final String authorization, final String applicationId, final String secretId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        // Quietly fine if it is already revoked or never existed, as it always was.
        ApplicationAccessService.parse(secretId)
            .flatMap(id -> secretRepository.findByPublicIdAndApplication(id, granted.application()))
            .filter(found -> found.getRevokedAt() == null)
            .ifPresent(found -> {
                // A secret Entra issued has to be revoked there too, or "revoked" here would be a lie that
                // leaves it working. If Entra refuses, it stays active here as well, so nothing is claimed
                // that is not true.
                if (found.getEntraKeyId() != null) {
                    credentials.revokeSecret(granted.application().getClientId(), found.getEntraKeyId());
                }
                secretRepository.save(found.toBuilder().revokedAt(now()).build());
            });
        return new OkResponse(true);
    }

    public ApplicationEnvelope connectApi(final String authorization, final String applicationId,
        final ConnectApiRequest request) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        if (isBlank(request.getId()) || isBlank(request.getName())
            || request.getId().length() > MAX_NAME || request.getName().length() > MAX_NAME) {
            throw badRequest("API id and name are required.");
        }
        List<ConnectedApi> connected = new ArrayList<>(views.connectedApis(granted.application().getConnectedApis()));
        if (connected.stream().anyMatch(api -> api.id().equals(request.getId()))) {
            throw conflict("That API is already connected.");
        }
        connected.add(new ConnectedApi(request.getId(), request.getName()));
        if (!credentials.enabled()) {
            return withConnectedApis(granted, connected);
        }
        return connectWithSubscription(granted, request.getId(), connected);
    }

    // Each API an application is connected to gets its own APIM Subscription Key. The Product is looked up
    // first, so an API with none is turned away before anything exists to undo.
    private ApplicationEnvelope connectWithSubscription(final Access granted, final String apiId,
        final List<ConnectedApi> connected) {
        ApplicationEntity application = granted.application();
        String productId = credentials.productFor(apiId);
        ApimSubscriptionClient.Subscription subscription = credentials.subscribe(application.getName(), productId);
        try {
            return transactionTemplate.execute(status -> {
                apiKeyRepository.save(ApplicationApiKeyEntity.builder()
                    .application(application)
                    .apiShortCode(apiId)
                    .publisherId(productId)
                    .subscriptionKey(subscription.subscriptionKey())
                    .subscriptionName(subscription.subscriptionName())
                    .createdAt(now())
                    .build());
                return withConnectedApis(granted, connected);
            });
        } catch (RuntimeException failure) {
            credentials.undoSubscription(subscription.subscriptionName());
            throw failure;
        }
    }

    public ApplicationEnvelope disconnectApi(final String authorization, final String applicationId,
        final String apiId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        List<ConnectedApi> remaining = views.connectedApis(granted.application().getConnectedApis()).stream()
            .filter(api -> !api.id().equals(apiId))
            .toList();
        List<ApplicationApiKeyEntity> keys =
            apiKeyRepository.findByApplicationIdAndApiShortCode(granted.application().getId(), apiId);
        if (keys.isEmpty()) {
            return withConnectedApis(granted, remaining);
        }
        // The subscription is deleted before the row that remembers it, so a failure leaves the key
        // visible and the disconnect something that can be tried again.
        keys.stream()
            .map(ApplicationApiKeyEntity::getSubscriptionName)
            .filter(Objects::nonNull)
            .forEach(credentials::unsubscribe);
        return transactionTemplate.execute(status -> {
            apiKeyRepository.deleteAll(keys);
            return withConnectedApis(granted, remaining);
        });
    }

    private ApplicationEnvelope withConnectedApis(final Access granted, final List<ConnectedApi> connected) {
        ApplicationEntity saved = applicationRepository.save(
            granted.application().toBuilder().connectedApis(views.write(connected)).build());
        return new ApplicationEnvelope(views.view(saved, granted.role()));
    }

    private ApplicationSecretEntity secretFor(final ApplicationEntity application, final String secret,
        final String hash, final String entraKeyId, final LocalDateTime createdAt) {
        return ApplicationSecretEntity.builder()
            .application(application)
            .keyHash(hash)
            .keyPreview(ClientSecretGenerator.previewOf(secret))
            .entraKeyId(entraKeyId)
            .createdAt(createdAt)
            .build();
    }

    private String validName(final String name) {
        if (isBlank(name)) {
            throw badRequest("Enter an application name.");
        }
        if (name.trim().length() > MAX_NAME) {
            throw badRequest("Application name must be 200 characters or fewer.");
        }
        return name.trim();
    }

    private String validEnvironment(final String environment) {
        if (environment == null || !KNOWN_ENVIRONMENTS.contains(environment)) {
            throw badRequest("Select a valid environment.");
        }
        if (!Arrays.asList(allowedEnvironments.split(",")).stream().map(String::trim).toList()
            .contains(environment)) {
            throw badRequest("The " + environment + " environment is not available yet.");
        }
        return environment;
    }

    private String optionalText(final String value, final String label) {
        if (value != null && value.length() > MAX_TEXT) {
            throw badRequest(label + " must be 2000 characters or fewer.");
        }
        return value;
    }

    // An empty string is allowed, and clears the field. Anything else must be a web address: these
    // are shown back as links, and a javascript: or data: address stored here would be a way to run
    // script in whoever clicks it.
    private String validUrl(final String url) {
        if (url.isEmpty()) {
            return url;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (url.length() > MAX_TEXT || !(lower.startsWith("https://") || lower.startsWith("http://"))) {
            throw badRequest("Enter a valid URL starting with http:// or https://.");
        }
        return url;
    }

    private Map<String, String> validAttributes(final Map<String, String> attributes) {
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw tooManyAttributes();
        }
        attributes.forEach((key, value) -> {
            if (isBlank(key) || value == null || key.length() > MAX_ATTRIBUTE_LENGTH
                || value.length() > MAX_ATTRIBUTE_LENGTH) {
                throw tooManyAttributes();
            }
        });
        return attributes;
    }

    private ResponseStatusException tooManyAttributes() {
        return badRequest("Custom attributes need a name and a value of 200 characters or fewer each, "
            + "and there can be no more than 50.");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(final String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(final String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
