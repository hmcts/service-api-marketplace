package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.AddDomainRequest;
import uk.gov.hmcts.cp.domain.AdminUserResponse;
import uk.gov.hmcts.cp.domain.AdminUsersResponse;
import uk.gov.hmcts.cp.domain.CreateOrganisationRequest;
import uk.gov.hmcts.cp.domain.EntraApplicationSummary;
import uk.gov.hmcts.cp.domain.EntraApplicationsResponse;
import uk.gov.hmcts.cp.domain.EntraProfileResponse;
import uk.gov.hmcts.cp.domain.OrganisationResponse;
import uk.gov.hmcts.cp.domain.OrganisationsResponse;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.services.OrganisationAdminService;
import uk.gov.hmcts.cp.services.OrganisationApprovalService;
import uk.gov.hmcts.cp.services.OrganisationApprovalService.Caller;

/**
 * For people who sign in with Entra, and the admins who keep the list of approved domains. Every call carries
 * the Entra token as
 * {@code Authorization: Bearer}; none of it exists (404) unless ORGANISATION_APPROVAL=on.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrganisationApprovalController {

    private static final String AUTHORIZATION = "Authorization";

    private final OrganisationApprovalService approvals;
    private final OrganisationAdminService admin;
    private final ApplicationRepository applicationRepository;

    /** Who the marketplace thinks the caller is; refused if their email domain is not approved. */
    @GetMapping("/entra/me")
    public ResponseEntity<EntraProfileResponse> me(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        return ResponseEntity.ok(approvals.profile(approvals.caller(authorization)));
    }

    /** The applications the caller can see; refused with DOMAIN_NOT_APPROVED unless their domain is approved. */
    @GetMapping("/entra/applications")
    public ResponseEntity<EntraApplicationsResponse> applications(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        Caller caller = approvals.caller(authorization);
        return ResponseEntity.ok(new EntraApplicationsResponse(
            applicationRepository.findAccessibleTo(caller.user().getId(), caller.user().getEmail()).stream()
                .map(application -> new EntraApplicationSummary(application.getName(), application.getEnvironment(),
                    application.getClientId()))
                .toList()));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<AdminUsersResponse> users(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @RequestParam(value = "status", required = false) final String status) {
        approvals.admin(authorization);
        return ResponseEntity.ok(admin.users(status));
    }

    @GetMapping("/admin/organisations")
    public ResponseEntity<OrganisationsResponse> organisations(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        approvals.admin(authorization);
        return ResponseEntity.ok(admin.organisations());
    }

    @GetMapping("/admin/organisations/{id}/users")
    public ResponseEntity<AdminUsersResponse> organisationUsers(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        approvals.admin(authorization);
        return ResponseEntity.ok(admin.usersOf(id));
    }

    @PostMapping("/admin/organisations")
    public ResponseEntity<OrganisationResponse> createOrganisation(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @RequestBody final CreateOrganisationRequest request) {
        approvals.admin(authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createOrganisation(request.name()));
    }

    @PostMapping("/admin/organisations/{id}/domains")
    public ResponseEntity<OrganisationResponse> addDomain(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id, @RequestBody final AddDomainRequest request) {
        approvals.admin(authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.addDomain(id, request.domain()));
    }

    @DeleteMapping("/admin/organisations/{id}/domains/{domain}")
    public ResponseEntity<OrganisationResponse> removeDomain(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id, @PathVariable("domain") final String domain) {
        approvals.admin(authorization);
        return ResponseEntity.ok(admin.removeDomain(id, domain));
    }

    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<AdminUserResponse> suspend(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        return ResponseEntity.ok(admin.suspend(id, approvals.admin(authorization).email()));
    }

    @PostMapping("/admin/users/{id}/reinstate")
    public ResponseEntity<AdminUserResponse> reinstate(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        return ResponseEntity.ok(admin.reinstate(id, approvals.admin(authorization).email()));
    }
}
