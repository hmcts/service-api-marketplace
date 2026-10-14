package uk.gov.hmcts.cp.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraIdentity;
import uk.gov.hmcts.cp.domain.EntraProfileResponse;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may use the marketplace, for people who sign in with Entra: the user is identified from a validated token,
 * saved once keyed by their Entra object id, and placed in an organisation by the domain of their email address.
 * A domain on the approved list makes them ACTIVE; any other leaves them HELD until an admin approves them. The
 * check is made on every call, so a removed domain or a suspension takes effect on the next request.
 *
 * <p>Off unless ORGANISATION_APPROVAL=on, so nothing changes until it is switched on; anything but on or off stops
 * the service starting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganisationApprovalService {

    public static final String ACTIVE = "ACTIVE";
    public static final String HELD = "HELD";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String NOT_APPROVED = "ORGANISATION_NOT_APPROVED";

    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String CONSUMER = "consumer";
    private static final String NOT_FOUND = "Not found.";

    private final EntraTokenVerifier verifier;
    private final UserRepository userRepository;
    private final OrganisationDomainRepository domainRepository;
    private final TransactionTemplate transactionTemplate;
    private final ClockService clockService;

    // Not final so tests can set it; the default leaves the service exactly as it was.
    @Value("${ORGANISATION_APPROVAL:off}")
    private String mode = OFF;

    // Comma-separated emails of the people who may use the admin endpoints.
    @Value("${ADMIN_EMAILS:}")
    private String adminEmails = "";

    /** The person who made a call: who the token says, and the row we hold for them. */
    public record Caller(EntraIdentity identity, UserEntity user) {
    }

    @PostConstruct
    void requireKnownMode() {
        String normalised = normalised();
        if (!ON.equals(normalised) && !OFF.equals(normalised)) {
            throw new IllegalStateException("ORGANISATION_APPROVAL must be 'on' or 'off', not '" + mode + "'");
        }
    }

    public boolean enabled() {
        return ON.equals(normalised());
    }

    /** Validates the token and returns the caller, creating or refreshing their row. Held people are returned too. */
    public Caller caller(final String authorization) {
        requireEnabled();
        EntraIdentity identity = verifier.verify(authorization);
        if (identity.oid() == null || identity.email() == null || EmailDomain.of(identity.email()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your sign-in did not include an email address.");
        }
        try {
            return new Caller(identity, transactionTemplate.execute(status -> upsert(identity)));
        } catch (DataIntegrityViolationException e) {
            // Two first calls at once, or an address that already belongs to a different account here.
            return new Caller(identity, userRepository.findByEntraObjectId(identity.oid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email address already exists.")));
        }
    }

    /**
     * As {@link #caller}, and refuses anyone who is not ACTIVE with the reason.
     */
    public Caller activeCaller(final String authorization) {
        Caller caller = caller(authorization);
        String status = caller.user().getStatus();
        if (HELD.equals(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                NOT_APPROVED + ": your organisation is not approved for the API Marketplace yet.");
        }
        if (SUSPENDED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your access has been suspended.");
        }
        return caller;
    }

    /**
     * As {@link #caller}, and answers 404 (so the endpoint is not discoverable) unless they are an admin.
     */
    public Caller adminCaller(final String authorization) {
        Caller caller = caller(authorization);
        Set<String> admins = Arrays.stream(adminEmails == null ? new String[0] : adminEmails.split(","))
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
        if (!admins.contains(caller.identity().email().trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return caller;
    }

    public EntraProfileResponse profile(final Caller caller) {
        UserEntity user = caller.user();
        return new EntraProfileResponse(caller.identity().oid(), user.getEmail(), name(user),
            user.getOrganisation() == null ? null : user.getOrganisation().getName(), user.getStatus(), user.getRole());
    }

    /**
     * Sets an organisation and status from the person's email domain: a listed domain makes them ACTIVE in that
     * organisation, an unlisted one makes them HELD with none. Someone suspended stays suspended.
     */
    public UserEntity applyDomainRule(final UserEntity user) {
        if (SUSPENDED.equals(user.getStatus())) {
            return user;
        }
        Optional<OrganisationDomainEntity> domain = EmailDomain.of(user.getEmail())
            .flatMap(domainRepository::findByDomainIgnoreCase);
        if (domain.isPresent()) {
            return user.toBuilder().organisation(domain.get().getOrganisation()).status(ACTIVE).build();
        }
        // An unlisted domain: held, unless an admin has placed this person in an organisation by hand.
        return user.getOrganisation() != null && ACTIVE.equals(user.getStatus())
            && user.getApprovedBy() != null ? user : user.toBuilder().organisation(null).status(HELD).build();
    }

    private UserEntity upsert(final EntraIdentity identity) {
        Optional<UserEntity> existing = userRepository.findByEntraObjectId(identity.oid());
        String address = identity.email().trim().toLowerCase(Locale.ROOT);
        if (existing.isEmpty() && userRepository.findByEmail(address).isPresent()) {
            // Never linked to an account that already uses this address: that would let an unverified address
            // take over someone else's account.
            throw new DataIntegrityViolationException("email already used by another account");
        }

        String[] names = splitName(identity.name(), address);
        UserEntity base = existing
            .map(user -> user.toBuilder().email(address).firstName(names[0]).lastName(names[1]).build())
            .orElseGet(() -> UserEntity.builder()
                .email(address)
                .firstName(names[0])
                .lastName(names[1])
                .role(CONSUMER)
                .status(HELD)
                .entraObjectId(identity.oid())
                .build());
        UserEntity ruled = applyDomainRule(base);
        if (existing.isPresent() && ruled.equals(existing.get())) {
            return existing.get();
        }
        UserEntity saved = userRepository.save(ruled);
        log.info("Entra user {} is {} (organisation {})", identity.oid(), saved.getStatus(),
            saved.getOrganisation() == null ? "none" : saved.getOrganisation().getId());
        return saved;
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }

    private String normalised() {
        return mode == null ? OFF : mode.trim().toLowerCase(Locale.ROOT);
    }

    static String name(final UserEntity user) {
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (user.getFirstName() + " " + last).trim();
    }

    // Entra gives one display name; the first word is the first name and the rest the last. Both columns are required,
    // so a missing name falls back to the part of the address before the @.
    static String[] splitName(final String displayName, final String email) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            return new String[] {email.substring(0, email.indexOf('@')), ""};
        }
        int space = name.indexOf(' ');
        if (space < 0) {
            return new String[] {name, ""};
        }
        return new String[] {name.substring(0, space), name.substring(space + 1).trim()};
    }
}
