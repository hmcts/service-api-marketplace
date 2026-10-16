package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern OBJECT_ID =
        Pattern.compile("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$");

    private final UserRepository userRepository;
    private final ClockService clockService;

    /** 404 rather than 401: the caller is signed in to Entra, it is this service that has no row for them. */
    public UserEntity requireSignedIn(final String requestingUserId) {
        if (requestingUserId == null || requestingUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing requestingUserId.");
        }
        return userRepository.findByEntraObjectId(requestingUserId.trim().toLowerCase(Locale.UK))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sign in again."));
    }

    /** Everything a user owns in Entra and APIM is named from here, so one person's things sort together. */
    public static String namePrefix(final UserEntity user) {
        return String.format("amp-user%03d-", user.getId());
    }

    public UserResponse signIn(final SessionRequest request) {
        final String oid = requireObjectId(request.objectId());
        try {
            return upsert(oid, request);
        } catch (DataIntegrityViolationException first) {
            log.info("Entra sign in for a new user raced another; trying once more");
            try {
                return upsert(oid, request);
            } catch (DataIntegrityViolationException again) {
                log.warn("Entra sign in clashed on a unique email address or object id");
                throw emailInUse();
            }
        }
    }

    private UserResponse upsert(final String oid, final SessionRequest request) {
        final LocalDateTime now = LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC);
        final String email = request.email().trim().toLowerCase(Locale.UK);

        final UserEntity user = userRepository.findByEntraObjectId(oid).orElseGet(() -> newUser(oid, now));
        if (!UserEntity.ACTIVE.equals(user.getStatus())) {
            log.warn("Entra sign in refused for userId {}, which is not active", user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not active.");
        }

        final Optional<UserEntity> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent() && !oid.equals(sameEmail.get().getEntraObjectId())) {
            log.warn("Entra sign in brought the email address of userId {}", sameEmail.get().getId());
            throw emailInUse();
        }

        final boolean created = user.getId() == null;
        user.setEmail(email);
        final String[] names = request.name() == null ? new String[0] : request.name().trim().split("\\s+", 2);
        user.setFirstName(names.length > 0 ? names[0] : "");
        user.setLastName(names.length > 1 ? names[1] : "");
        user.setLastLoginAt(now);
        final UserEntity saved = userRepository.saveAndFlush(user);
        log.info("Entra sign in {} userId {}", created ? "created" : "updated", saved.getId());
        return UserResponse.from(saved);
    }

    private static UserEntity newUser(final String oid, final LocalDateTime now) {
        final UserEntity user = new UserEntity();
        user.setEntraObjectId(oid);
        user.setStatus(UserEntity.ACTIVE);
        user.setCreatedAt(now);
        return user;
    }

    private static String requireObjectId(final String entraObjectId) {
        if (entraObjectId == null || !OBJECT_ID.matcher(entraObjectId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid Entra object id.");
        }
        return entraObjectId.toLowerCase(Locale.UK);
    }

    private static ResponseStatusException emailInUse() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "This email address is already used by another account.");
    }
}
