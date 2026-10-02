package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.AddTeamMemberRequest;
import uk.gov.hmcts.cp.domain.ApplicationDetailResponse;
import uk.gov.hmcts.cp.domain.ApplicationEnvelope;
import uk.gov.hmcts.cp.domain.ApplicationListResponse;
import uk.gov.hmcts.cp.domain.ConnectApiRequest;
import uk.gov.hmcts.cp.domain.CreateApplicationRequest;
import uk.gov.hmcts.cp.domain.CreatedApplicationResponse;
import uk.gov.hmcts.cp.domain.NewApiKeyResponse;
import uk.gov.hmcts.cp.domain.OkResponse;
import uk.gov.hmcts.cp.domain.TeamMemberEnvelope;
import uk.gov.hmcts.cp.domain.TeamMembersResponse;
import uk.gov.hmcts.cp.domain.UpdateApplicationRequest;
import uk.gov.hmcts.cp.services.ApplicationManagementService;
import uk.gov.hmcts.cp.services.TeamMemberService;

/**
 * "My applications", as the frontend calls it: under /api, bearer-token authenticated. Separate from
 * ApplicationController, which registers an application through Entra and APIM for callers that
 * authenticate with the older requestingUserId header.
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationManagementController {

    private final ApplicationManagementService applications;
    private final TeamMemberService teamMembers;

    @GetMapping
    public ResponseEntity<ApplicationListResponse> list(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization) {
        return ResponseEntity.ok(applications.list(authorization));
    }

    @PostMapping
    public ResponseEntity<CreatedApplicationResponse> create(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @RequestBody final CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.create(authorization, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDetailResponse> detail(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id) {
        return ResponseEntity.ok(applications.detail(authorization, id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApplicationEnvelope> update(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @RequestBody final UpdateApplicationRequest request) {
        return ResponseEntity.ok(applications.update(authorization, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id) {
        applications.delete(authorization, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/api-keys")
    public ResponseEntity<NewApiKeyResponse> newSecret(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.newSecret(authorization, id));
    }

    @DeleteMapping("/{id}/api-keys/{keyId}")
    public ResponseEntity<OkResponse> revokeSecret(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @PathVariable final String keyId) {
        return ResponseEntity.ok(applications.revokeSecret(authorization, id, keyId));
    }

    @PostMapping("/{id}/connected-apis")
    public ResponseEntity<ApplicationEnvelope> connectApi(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @RequestBody final ConnectApiRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.connectApi(authorization, id, request));
    }

    @DeleteMapping("/{id}/connected-apis/{apiId}")
    public ResponseEntity<ApplicationEnvelope> disconnectApi(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @PathVariable final String apiId) {
        return ResponseEntity.ok(applications.disconnectApi(authorization, id, apiId));
    }

    @GetMapping("/{id}/team-members")
    public ResponseEntity<TeamMembersResponse> listTeam(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id) {
        return ResponseEntity.ok(teamMembers.list(authorization, id));
    }

    @PostMapping("/{id}/team-members")
    public ResponseEntity<TeamMemberEnvelope> addTeamMember(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @RequestBody final AddTeamMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamMembers.add(authorization, id, request));
    }

    @DeleteMapping("/{id}/team-members/{memberId}")
    public ResponseEntity<OkResponse> removeTeamMember(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authorization,
        @PathVariable final String id,
        @PathVariable final String memberId) {
        return ResponseEntity.ok(teamMembers.remove(authorization, id, memberId));
    }
}
