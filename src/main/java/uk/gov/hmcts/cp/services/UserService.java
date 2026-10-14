package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraSignInRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.UserMapper;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final UserMapper userMapper;
    private final ClockService clockService;

    public UserResponse validateUser(final long requestingUserId) {
        UserEntity user = userRepository.findById(requestingUserId)
            .orElseThrow(() -> {
                log.warn("Requesting user not found: {}", requestingUserId);
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requesting user not found.");
            });
        return userMapper.fromEntity(user);
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream().map(userMapper::fromEntity).toList();
    }

    public UserResponse getUser(final String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail.toLowerCase(Locale.UK))
            .orElseThrow(() -> {
                // Deliberately does not echo the address back, matching the spec's note
                // that responses must not confirm whether an email is registered.
                log.warn("User not found for the requested email address");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
            });
        return userMapper.fromEntity(user);
    }

    private static final Pattern OBJECT_ID =
        Pattern.compile("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$");
    private static final String ACTIVE = "ACTIVE";

    /**
     * Records someone Entra External ID has just signed in, and returns their account. Entra owns
     * the email address only: a known person has their email brought up to date and nothing else;
     * an account that already has this email is linked and keeps its name and organisation;
     * anyone else gets a new account in the default organisation, with no password.
     */
    public UserResponse signInWithEntra(final String entraObjectId, final EntraSignInRequest request) {
        final String oid = requireObjectId(entraObjectId);
        final String email = request.getEmail().trim().toLowerCase(Locale.UK);

        final Optional<UserEntity> known = userRepository.findByEntraObjectId(oid);
        if (known.isPresent()) {
            final UserEntity user = requireActive(known.get());
            if (email.equals(user.getEmail())) {
                return save(user);
            }
            if (userRepository.findByEmail(email).isPresent()) {
                log.warn("Entra sign in for userId {} brought an email address another account has", user.getId());
                throw conflict();
            }
            log.info("Entra sign in updated the email address of userId {}", user.getId());
            return save(user.toBuilder().email(email).build());
        }

        final Optional<UserEntity> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent()) {
            final UserEntity user = requireActive(sameEmail.get());
            if (user.getEntraObjectId() != null) {
                log.warn("Entra sign in matched userId {}, which belongs to another Entra user", user.getId());
                throw conflict();
            }
            log.info("Entra sign in linked existing userId {}", user.getId());
            return save(user.toBuilder().entraObjectId(oid).build());
        }

        final UserResponse created = save(UserEntity.builder()
            .organisation(defaultOrganisation())
            .firstName(trimToEmpty(request.getFirstName()))
            .lastName(trimToEmpty(request.getLastName()))
            .email(email)
            .status(ACTIVE)
            .entraObjectId(oid)
            .build());
        log.info("Entra sign in created userId {}", created.getId());
        return created;
    }

    /** Every Entra sign-in ends here, so every one is recorded as the latest. */
    private UserResponse save(final UserEntity user) {
        final LocalDateTime now = LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);
        try {
            return userMapper.fromEntity(userRepository.saveAndFlush(user.toBuilder().lastSignedInAt(now).build()));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ins for one person, or one email, racing; the unique indexes decide.
            log.warn("Entra sign in lost a race for an email address or object id");
            throw conflict();
        }
    }

    private OrganisationEntity defaultOrganisation() {
        final String name = AccountService.DEFAULT_ORGANISATION;
        return organisationRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            try {
                return organisationRepository.save(OrganisationEntity.builder().name(name).build());
            } catch (DataIntegrityViolationException e) {
                return organisationRepository.findByNameIgnoreCase(name).orElseThrow(() -> e);
            }
        });
    }

    private static String requireObjectId(final String entraObjectId) {
        if (entraObjectId == null || !OBJECT_ID.matcher(entraObjectId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid Entra object id.");
        }
        return entraObjectId.toLowerCase(Locale.UK);
    }

    private static UserEntity requireActive(final UserEntity user) {
        if (!ACTIVE.equals(user.getStatus())) {
            log.warn("Entra sign in refused for userId {}, which is not active", user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not active.");
        }
        return user;
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "This email address is already used by another account.");
    }

    private static String trimToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }
}
