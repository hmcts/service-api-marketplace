package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
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
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
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
import java.util.UUID;

/**
 * "My applications": create, view, change and delete an application, manage its client secrets, and
 * connect it to APIs - the behaviour the frontend was built against on amp-auth. Who may do what is
 * decided in {@link ApplicationAccessService}; each method here names the lowest role it needs.
 *
 * <p>The client secret is generated here and shown once: only its bcrypt hash is stored. Registering
 * the application with Entra and issuing APIM subscription keys is a separate path (see
 * ApplicationService); this one needs no external system, so it works wherever the database does.
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

        // Hashed before the transaction opens: bcrypt is deliberately slow, and a transaction should
        // not hold a connection while it runs.
        String secret = secretGenerator.generate();
        String hash = passwordService.hash(secret);
        UUID publicId = UUID.randomUUID();
        LocalDateTime now = now();

        try {
            ApplicationEntity saved = transactionTemplate.execute(status -> {
                ApplicationEntity application = applicationRepository.save(ApplicationEntity.builder()
                    .user(owner)
                    .name(name)
                    .environment(environment)
                    .description(description)
                    .publicId(publicId)
                    .clientId(publicId.toString())
                    .createdAt(now)
                    .build());
                secretRepository.save(secretFor(application, secret, hash, now));
                return application;
            });
            log.info("Application {} created for userId {}", publicId, caller.id());
            return new CreatedApplicationResponse(views.view(saved, ViewerRole.OWNER), secret);
        } catch (DataIntegrityViolationException e) {
            // Two creations of the same name racing past the check above; the unique index decides.
            log.warn("Application creation lost a race for a name");
            throw conflict(DUPLICATE);
        }
    }

    public ApplicationDetailResponse detail(final String authorization, final String applicationId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        return new ApplicationDetailResponse(
            views.view(granted.application(), granted.role()),
            secretRepository.findByApplicationOrderByCreatedAtDesc(granted.application()).stream()
                .map(views::summary)
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
        applicationRepository.delete(granted.application());
        log.info("Application {} deleted by userId {}", applicationId, granted.caller().id());
    }

    public NewApiKeyResponse newSecret(final String authorization, final String applicationId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        String secret = secretGenerator.generate();
        ApplicationSecretEntity saved = secretRepository.save(
            secretFor(granted.application(), secret, passwordService.hash(secret), now()));
        return new NewApiKeyResponse(saved.getPublicId().toString(), secret);
    }

    public OkResponse revokeSecret(final String authorization, final String applicationId, final String secretId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        // Quietly fine if it is already revoked or never existed, as it always was.
        ApplicationAccessService.parse(secretId)
            .flatMap(id -> secretRepository.findByPublicIdAndApplication(id, granted.application()))
            .filter(found -> found.getRevokedAt() == null)
            .ifPresent(found -> secretRepository.save(found.toBuilder().revokedAt(now()).build()));
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
        return withConnectedApis(granted, connected);
    }

    public ApplicationEnvelope disconnectApi(final String authorization, final String applicationId,
        final String apiId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        List<ConnectedApi> remaining = views.connectedApis(granted.application().getConnectedApis()).stream()
            .filter(api -> !api.id().equals(apiId))
            .toList();
        return withConnectedApis(granted, remaining);
    }

    private ApplicationEnvelope withConnectedApis(final Access granted, final List<ConnectedApi> connected) {
        ApplicationEntity saved = applicationRepository.save(
            granted.application().toBuilder().connectedApis(views.write(connected)).build());
        return new ApplicationEnvelope(views.view(saved, granted.role()));
    }

    private ApplicationSecretEntity secretFor(final ApplicationEntity application, final String secret,
        final String hash, final LocalDateTime createdAt) {
        return ApplicationSecretEntity.builder()
            .application(application)
            .keyHash(hash)
            .keyPreview(ClientSecretGenerator.previewOf(secret))
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
