package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import uk.gov.hmcts.cp.domain.ApproveUserRequest;
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
 * For people who sign in with Entra, and the admins who approve them. Every call carries the Entra token as
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

    /** Who the marketplace thinks the caller is. Works while held, so the frontend can say why nothing else does. */
    @GetMapping("/entra/me")
    public ResponseEntity<EntraProfileResponse> me(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        return ResponseEntity.ok(approvals.profile(approvals.caller(authorization)));
    }

    /** The applications the caller can see; refused with ORGANISATION_NOT_APPROVED while they are held. */
    @GetMapping("/entra/applications")
    public ResponseEntity<EntraApplicationsResponse> applications(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        Caller caller = approvals.activeCaller(authorization);
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
        approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.users(status));
    }

    @GetMapping("/admin/organisations")
    public ResponseEntity<OrganisationsResponse> organisations(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization) {
        approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.organisations());
    }

    @GetMapping("/admin/organisations/{id}/users")
    public ResponseEntity<AdminUsersResponse> organisationUsers(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.usersOf(id));
    }

    @PostMapping("/admin/organisations")
    public ResponseEntity<OrganisationResponse> createOrganisation(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @RequestBody final CreateOrganisationRequest request) {
        approvals.adminCaller(authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createOrganisation(request.name()));
    }

    @PostMapping("/admin/organisations/{id}/domains")
    public ResponseEntity<OrganisationResponse> addDomain(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id, @RequestBody final AddDomainRequest request) {
        Caller caller = approvals.adminCaller(authorization);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(admin.addDomain(id, request.domain(), caller.identity().email()));
    }

    @PostMapping("/admin/users/{id}/approve")
    public ResponseEntity<AdminUserResponse> approve(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id, @RequestBody final ApproveUserRequest request) {
        Caller caller = approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.approve(id, request.organisationId(), caller.identity().email()));
    }

    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<AdminUserResponse> suspend(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        Caller caller = approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.suspend(id, caller.identity().email()));
    }

    @PostMapping("/admin/users/{id}/reinstate")
    public ResponseEntity<AdminUserResponse> reinstate(
        @RequestHeader(value = AUTHORIZATION, required = false) final String authorization,
        @PathVariable("id") final Integer id) {
        Caller caller = approvals.adminCaller(authorization);
        return ResponseEntity.ok(admin.reinstate(id, caller.identity().email()));
    }
}
