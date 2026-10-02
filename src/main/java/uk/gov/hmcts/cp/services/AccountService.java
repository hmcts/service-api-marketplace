package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AccountResponse;
import uk.gov.hmcts.cp.domain.AuthResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.MeResponse;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creating an account, signing in, and working out who a bearer token belongs to - the behaviour
 * the frontend was built against on amp-auth, and which the OpenAPI spec for this service has
 * always described. The messages are the frontend's: it shows them to the user as they are.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_EMAIL_LENGTH = 254;
    static final String DEFAULT_ORGANISATION = "Not specified";
    static final String ACTIVE = "ACTIVE";
    private static final List<String> ROLES = List.of("consumer", "producer");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;

    public AuthResponse register(final RegisterRequest request) {
        validate(request);
        // Before anything is written: an account that exists but cannot be signed in to is worse
        // than a registration that was refused.
        tokenService.requireConfigured();

        String email = normalise(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw emailTaken();
        }

        // Outside the try below, which is only for the email losing a race: a failure here is a real
        // error and must not be reported to the caller as "that email is taken".
        UserEntity toSave = UserEntity.builder()
            .organisation(organisationFor(request.getOrganisation()))
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .email(email)
            .passwordHash(passwordService.hash(request.getPassword()))
            .status(ACTIVE)
            .role(request.getRole())
            .build();

        UserEntity saved;
        try {
            saved = userRepository.save(toSave);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for one address racing past the check above; the unique index decides.
            log.warn("Registration lost a race for an email address");
            throw emailTaken();
        }
        log.info("Registered account for userId {}", saved.getId());
        return new AuthResponse(toResponse(saved), tokenService.issue(saved));
    }

    public AuthResponse login(final LoginRequest request) {
        if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required.");
        }
        tokenService.requireConfigured();

        UserEntity user = userRepository.findByEmail(normalise(request.getEmail())).orElse(null);
        boolean authenticated;
        if (user == null) {
            passwordService.spendTimeAsIfChecking(request.getPassword());
            authenticated = false;
        } else {
            authenticated = passwordService.matches(request.getPassword(), user.getPasswordHash())
                && ACTIVE.equals(user.getStatus());
        }
        if (!authenticated) {
            // The same answer whether the address is unknown, the password is wrong, or the account
            // is not active - the response must not say which.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }
        return new AuthResponse(toResponse(user), tokenService.issue(user));
    }

    public MeResponse currentUser(final String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")
            || authorizationHeader.length() == "Bearer ".length()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        }
        TokenService.Claims claims = tokenService.verify(authorizationHeader.substring("Bearer ".length()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Session expired. Please sign in again."));
        UserEntity user = userRepository.findById(claims.userId())
            .filter(found -> ACTIVE.equals(found.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in."));
        return new MeResponse(toResponse(user));
    }

    private void validate(final RegisterRequest request) {
        if (isBlank(request.getFirstName()) || isBlank(request.getLastName()) || isBlank(request.getEmail())
            || isBlank(request.getRole()) || isBlank(request.getPassword())) {
            throw badRequest("Missing required fields.");
        }
        if (!EMAIL.matcher(request.getEmail()).matches()) {
            throw badRequest("Enter a valid email address.");
        }
        if (!ROLES.contains(request.getRole())) {
            throw badRequest("Role must be \"consumer\" or \"producer\".");
        }
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Password must be at least 12 characters long.");
        }
        if (PasswordService.isTooLong(request.getPassword())) {
            throw badRequest("Password must be no more than 72 bytes long.");
        }
        if (request.getFirstName().length() > MAX_NAME_LENGTH || request.getLastName().length() > MAX_NAME_LENGTH
            || (request.getOrganisation() != null && request.getOrganisation().length() > MAX_NAME_LENGTH)) {
            throw badRequest("Names must be no more than 200 characters long.");
        }
        if (request.getEmail().length() > MAX_EMAIL_LENGTH) {
            throw badRequest("Enter a valid email address.");
        }
    }

    // "HMCTS" and "hmcts" are one organisation. Created on first use because registration takes it as
    // free text; a person who gives none is filed under a placeholder, since every user belongs to one.
    private OrganisationEntity organisationFor(final String requested) {
        String name = isBlank(requested) ? DEFAULT_ORGANISATION : requested.trim();
        return organisationRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            try {
                return organisationRepository.save(OrganisationEntity.builder().name(name).build());
            } catch (DataIntegrityViolationException e) {
                return organisationRepository.findByNameIgnoreCase(name).orElseThrow(() -> e);
            }
        });
    }

    private AccountResponse toResponse(final UserEntity user) {
        return new AccountResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
            user.getRole());
    }

    private String normalise(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(final String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account with these details could not be created.");
    }
}
