package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.Locale;

/**
 * What an admin does to the approved list and to people waiting on it. The caller has already been checked as an
 * admin; everything recorded against a person names that admin and the time.
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
    private final OrganisationApprovalService approvals;
    private final TransactionTemplate transactionTemplate;
    private final ClockService clockService;

    public AdminUsersResponse users(final String status) {
        String wanted = status == null || status.isBlank() ? OrganisationApprovalService.HELD
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

    /** Lists a domain for an organisation, and makes everyone held on that domain active in it. */
    public OrganisationResponse addDomain(final Integer organisationId, final String domain, final String adminEmail) {
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
        LocalDateTime now = now();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                domainRepository.save(OrganisationDomainEntity.builder()
                    .organisation(organisation).domain(normalised).createdAt(now).build());
                for (UserEntity held : userRepository.findByStatusAndEmailEndingWithIgnoreCase(
                    OrganisationApprovalService.HELD, "@" + normalised)) {
                    userRepository.save(held.toBuilder().organisation(organisation)
                        .status(OrganisationApprovalService.ACTIVE).approvedBy(adminEmail).approvedAt(now).build());
                }
            });
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That domain is already listed.");
        }
        log.info("Domain {} listed for organisation {} by an admin", normalised, organisationId);
        return view(organisation);
    }

    /** Approves one person into an organisation, and grants the producer role if they asked for it. */
    public AdminUserResponse approve(final Integer userId, final Integer organisationId, final String adminEmail) {
        UserEntity user = user(userId);
        OrganisationEntity organisation = organisation(organisationId);
        UserEntity.UserEntityBuilder builder = user.toBuilder().organisation(organisation)
            .status(OrganisationApprovalService.ACTIVE).approvedBy(adminEmail).approvedAt(now());
        if (user.getRequestedRole() != null) {
            builder.role(user.getRequestedRole()).requestedRole(null);
        }
        return view(userRepository.save(builder.build()));
    }

    public AdminUserResponse suspend(final Integer userId, final String adminEmail) {
        UserEntity user = user(userId);
        return view(userRepository.save(user.toBuilder().status(OrganisationApprovalService.SUSPENDED)
            .approvedBy(adminEmail).approvedAt(now()).build()));
    }

    /** Back to active if they have an organisation or a listed domain; otherwise held again. */
    public AdminUserResponse reinstate(final Integer userId, final String adminEmail) {
        UserEntity user = user(userId);
        UserEntity back = user.toBuilder().status(user.getOrganisation() == null
            ? OrganisationApprovalService.HELD : OrganisationApprovalService.ACTIVE)
            .approvedBy(adminEmail).approvedAt(now()).build();
        return view(userRepository.save(approvals.applyDomainRule(back)));
    }

    private AdminUserResponse view(final UserEntity user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), OrganisationApprovalService.name(user),
            user.getStatus(), EmailDomain.of(user.getEmail()).orElse(""),
            user.getOrganisation() == null ? null : user.getOrganisation().getName(), user.getRequestedRole());
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
        return LocalDateTime.ofInstant(clockService.now(), java.time.ZoneId.systemDefault());
    }
}
