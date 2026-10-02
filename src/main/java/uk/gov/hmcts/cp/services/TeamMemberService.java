package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AddTeamMemberRequest;
import uk.gov.hmcts.cp.domain.OkResponse;
import uk.gov.hmcts.cp.domain.TeamMemberEnvelope;
import uk.gov.hmcts.cp.domain.TeamMembersResponse;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;
import uk.gov.hmcts.cp.repository.ApplicationTeamMemberRepository;
import uk.gov.hmcts.cp.services.ApplicationAccessService.Access;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Who else can work on an application. A developer can see the team; an administrator can change it.
 * The owner is not a row here: they always have full access and cannot be removed. People are matched
 * by email, so someone can be invited before they have registered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    private static final String ALREADY_MEMBER = "That person is already a team member on this application.";

    private final ApplicationAccessService access;
    private final ApplicationTeamMemberRepository teamMemberRepository;
    private final ApplicationViewFactory views;
    private final ClockService clockService;

    public TeamMembersResponse list(final String authorization, final String applicationId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.DEVELOPER);
        return new TeamMembersResponse(
            granted.application().getUser().getEmail(),
            teamMemberRepository.findByApplicationOrderByAddedAtAsc(granted.application()).stream()
                .map(views::teamMember)
                .toList());
    }

    public TeamMemberEnvelope add(final String authorization, final String applicationId,
        final AddTeamMemberRequest request) {
        final Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw badRequest("Enter an email address.");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!EmailAddresses.isValid(email)) {
            throw badRequest("Enter a valid email address.");
        }
        if (ViewerRole.teamRole(request.getRole()).isEmpty()) {
            throw badRequest("Select a permission level.");
        }
        if (email.equals(granted.caller().email().toLowerCase(Locale.ROOT))) {
            throw badRequest("You already have access to this application.");
        }
        if (email.equals(granted.application().getUser().getEmail().toLowerCase(Locale.ROOT))) {
            throw badRequest("That person already owns this application.");
        }
        if (teamMemberRepository.findByApplicationAndEmailIgnoreCase(granted.application(), email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_MEMBER);
        }
        try {
            return new TeamMemberEnvelope(views.teamMember(teamMemberRepository.save(
                ApplicationTeamMemberEntity.builder()
                    .application(granted.application())
                    .email(email)
                    .role(request.getRole())
                    .addedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC))
                    .build())));
        } catch (DataIntegrityViolationException e) {
            log.warn("Adding a team member lost a race for an email address");
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_MEMBER);
        }
    }

    public OkResponse remove(final String authorization, final String applicationId, final String memberId) {
        Access granted = access.require(authorization, applicationId, ViewerRole.ADMINISTRATOR);
        ApplicationTeamMemberEntity member = ApplicationAccessService.parse(memberId)
            .flatMap(id -> teamMemberRepository.findByPublicIdAndApplication(id, granted.application()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found."));
        if (member.getEmail().equalsIgnoreCase(granted.caller().email())) {
            throw badRequest("You cannot remove yourself from this application.");
        }
        teamMemberRepository.delete(member);
        return new OkResponse(true);
    }

    private ResponseStatusException badRequest(final String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
