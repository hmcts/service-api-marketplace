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
 * Who may use the marketplace, for people who sign in with Entra. The only rule is the list of pre-approved email
 * domains: a person whose address is on one of them is let in, as a member of that organisation; anyone else is
 * refused (403 DOMAIN_NOT_APPROVED) and nothing about them is saved. There is no queue and nobody approves
 * individuals. The person is identified from a validated token, saved once keyed by their Entra object id, and the
 * list is checked on every call, so a domain taken off it, or a suspension, takes effect on the next request.
 *
 * <p>Off unless ORGANISATION_APPROVAL=on, so nothing changes until it is switched on; anything but on or off stops
 * the service starting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganisationApprovalService {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String NOT_APPROVED = "DOMAIN_NOT_APPROVED";

    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String CONSUMER = "consumer";
    private static final String NOT_FOUND = "Not found.";

    private final EntraTokenVerifier verifier;
    private final UserRepository userRepository;
    private final OrganisationDomainRepository domainRepository;
    private final TransactionTemplate transactionTemplate;

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

    /**
     * Validates the token and returns the person, creating or refreshing their row. Refuses anyone whose email
     * domain is not on the approved list, and anyone who has been suspended.
     */
    public Caller caller(final String authorization) {
        requireEnabled();
        EntraIdentity identity = verifier.verify(authorization);
        if (identity.oid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        }
        String domain = identity.email() == null ? null : EmailDomain.of(identity.email()).orElse(null);
        if (domain == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your sign-in did not include an email address.");
        }
        OrganisationDomainEntity approved = domainRepository.findByDomainIgnoreCase(domain).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_APPROVED
                + ": the email domain " + domain + " is not on the approved list for the API Marketplace."));
        UserEntity user;
        try {
            user = transactionTemplate.execute(status -> upsert(identity, approved));
        } catch (DataIntegrityViolationException e) {
            // Two first calls at once, or an address that already belongs to a different account here.
            user = userRepository.findByEntraObjectId(identity.oid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email address already exists."));
        }
        if (SUSPENDED.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your access has been suspended.");
        }
        return new Caller(identity, user);
    }

    /**
     * Validates the token and answers 404 (so the endpoint is not discoverable) unless the person is an admin. An
     * admin is named in ADMIN_EMAILS and needs no approved domain, so the very first domain can be listed.
     */
    public EntraIdentity admin(final String authorization) {
        requireEnabled();
        EntraIdentity identity = verifier.verify(authorization);
        Set<String> admins = Arrays.stream(adminEmails == null ? new String[0] : adminEmails.split(","))
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
        if (identity.email() == null || !admins.contains(identity.email().trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return identity;
    }

    public EntraProfileResponse profile(final Caller caller) {
        UserEntity user = caller.user();
        return new EntraProfileResponse(caller.identity().oid(), user.getEmail(), name(user),
            user.getOrganisation() == null ? null : user.getOrganisation().getName(), user.getStatus(), user.getRole());
    }

    private UserEntity upsert(final EntraIdentity identity, final OrganisationDomainEntity approved) {
        Optional<UserEntity> existing = userRepository.findByEntraObjectId(identity.oid());
        String address = identity.email().trim().toLowerCase(Locale.ROOT);
        if (existing.isEmpty() && userRepository.findByEmail(address).isPresent()) {
            // Never linked to an account that already uses this address: that would let an unverified address
            // take over someone else's account.
            throw new DataIntegrityViolationException("email already used by another account");
        }
        String[] names = splitName(identity.name(), address);
        UserEntity wanted = existing
            .map(user -> user.toBuilder().email(address).firstName(names[0]).lastName(names[1])
                .organisation(approved.getOrganisation()).build())
            .orElseGet(() -> UserEntity.builder()
                .email(address)
                .firstName(names[0])
                .lastName(names[1])
                .role(CONSUMER)
                .status(ACTIVE)
                .organisation(approved.getOrganisation())
                .entraObjectId(identity.oid())
                .build());
        if (existing.isPresent() && wanted.equals(existing.get())) {
            return existing.get();
        }
        UserEntity saved = userRepository.save(wanted);
        log.info("Entra user {} is {} in organisation {}", identity.oid(), saved.getStatus(),
            saved.getOrganisation().getId());
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

    // Entra gives one display name; the first word is the first name and the rest the last. Both columns are
    // required, so a missing name falls back to the part of the address before the @.
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
