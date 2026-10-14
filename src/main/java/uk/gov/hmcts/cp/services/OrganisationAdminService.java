package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AdminUserResponse;
import uk.gov.hmcts.cp.domain.AdminUsersResponse;
import uk.gov.hmcts.cp.domain.OrganisationResponse;
import uk.gov.hmcts.cp.domain.OrganisationsResponse;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * What an admin does to the list of approved domains, and to a leaver. The caller has already been checked as an
 * admin. There is nothing here to approve a person: the list of domains is the approval. Suspending and
 * reinstating record who did it and when.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganisationAdminService {

    private static final int MAX_NAME = 120;
    private static final String NOT_FOUND = "Not found.";

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final OrganisationDomainRepository domainRepository;
    private final ClockService clockService;

    public AdminUsersResponse users(final String status) {
        String wanted = status == null || status.isBlank() ? OrganisationApprovalService.ACTIVE
            : status.trim().toUpperCase(Locale.ROOT);
        return new AdminUsersResponse(userRepository.findByStatusOrderByIdAsc(wanted).stream()
            .map(this::view).toList());
    }

    public AdminUsersResponse usersOf(final Integer organisationId) {
        organisation(organisationId);
        return new AdminUsersResponse(userRepository.findByOrganisationIdOrderByEmailAsc(organisationId).stream()
            .map(this::view).toList());
    }

    public OrganisationsResponse organisations() {
        return new OrganisationsResponse(organisationRepository.findAll().stream().map(this::view).toList());
    }

    public OrganisationResponse createOrganisation(final String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Organisation name is required (up to 120 characters).");
        }
        if (organisationRepository.findByNameIgnoreCase(trimmed).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That organisation already exists.");
        }
        try {
            return view(organisationRepository.save(OrganisationEntity.builder().name(trimmed).build()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That organisation already exists.");
        }
    }

    /** Puts a domain on the approved list for an organisation; everyone with an address on it may then sign in. */
    public OrganisationResponse addDomain(final Integer organisationId, final String domain) {
        OrganisationEntity organisation = organisation(organisationId);
        String normalised = EmailDomain.normalise(domain).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "That is not a valid domain, for example example.gov.uk."));
        if (EmailDomain.isShared(normalised)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A shared mail provider cannot belong to an organisation.");
        }
        if (domainRepository.findByDomainIgnoreCase(normalised).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That domain is already listed.");
        }
        try {
            domainRepository.save(OrganisationDomainEntity.builder()
                .organisation(organisation).domain(normalised).createdAt(now()).build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That domain is already listed.");
        }
        log.info("Domain {} approved for organisation {}", normalised, organisationId);
        return view(organisation);
    }

    /** Takes a domain off the list: its people are refused from their next call. */
    public OrganisationResponse removeDomain(final Integer organisationId, final String domain) {
        OrganisationEntity organisation = organisation(organisationId);
        OrganisationDomainEntity found = domainRepository.findByDomainIgnoreCase(domain == null ? "" : domain.trim())
            .filter(entry -> entry.getOrganisation().getId().equals(organisationId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        domainRepository.delete(found);
        log.info("Domain {} taken off the approved list for organisation {}", found.getDomain(), organisationId);
        return view(organisation);
    }

    public AdminUserResponse suspend(final Integer userId, final String adminEmail) {
        UserEntity user = user(userId);
        return view(userRepository.save(user.toBuilder().status(OrganisationApprovalService.SUSPENDED)
            .approvedBy(adminEmail).approvedAt(now()).build()));
    }

    public AdminUserResponse reinstate(final Integer userId, final String adminEmail) {
        UserEntity user = user(userId);
        return view(userRepository.save(user.toBuilder().status(OrganisationApprovalService.ACTIVE)
            .approvedBy(adminEmail).approvedAt(now()).build()));
    }

    private AdminUserResponse view(final UserEntity user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), OrganisationApprovalService.name(user),
            user.getStatus(), EmailDomain.of(user.getEmail()).orElse(""),
            user.getOrganisation() == null ? null : user.getOrganisation().getName());
    }

    private OrganisationResponse view(final OrganisationEntity organisation) {
        return new OrganisationResponse(organisation.getId(), organisation.getName(),
            domainRepository.findByOrganisationIdOrderByDomainAsc(organisation.getId()).stream()
                .map(OrganisationDomainEntity::getDomain).toList());
    }

    private UserEntity user(final Integer id) {
        return userRepository.findById(id == null ? -1 : id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private OrganisationEntity organisation(final Integer id) {
        return organisationRepository.findById(id == null ? -1 : id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clockService.now(), ZoneId.systemDefault());
    }
}
