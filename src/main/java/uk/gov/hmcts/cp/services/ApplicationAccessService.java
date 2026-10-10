package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AccountResponse;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.repository.ApplicationRepository;
import uk.gov.hmcts.cp.repository.ApplicationTeamMemberRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Works out who is calling and what they may do on one application. Someone with no access at all
 * gets exactly the answer an application that does not exist gets, so this never confirms that an
 * application exists to a person who cannot see it.
 */
@Service
@RequiredArgsConstructor
public class ApplicationAccessService {

    static final String NOT_FOUND = "Application not found.";

    private final AccountService accountService;
    private final ApplicationRepository applicationRepository;
    private final ApplicationTeamMemberRepository teamMemberRepository;

    public record Caller(int id, String email) {
    }

    public record Access(Caller caller, ApplicationEntity application, ViewerRole role) {
    }

    public Caller caller(final String authorization) {
        AccountResponse user = accountService.currentUser(authorization).user();
        return new Caller(user.id(), user.email());
    }

    public Access require(final String authorization, final String applicationId, final ViewerRole minimum) {
        Caller caller = caller(authorization);
        ApplicationEntity application = parse(applicationId)
            .flatMap(applicationRepository::findByPublicId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        ViewerRole role = roleOf(application, caller)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!role.atLeast(minimum)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to do this.");
        }
        return new Access(caller, application, role);
    }

    public Optional<ViewerRole> roleOf(final ApplicationEntity application, final Caller caller) {
        if (application.getUser().getId() == caller.id()) {
            return Optional.of(ViewerRole.OWNER);
        }
        return teamMemberRepository.findByApplicationAndEmailIgnoreCase(application, caller.email())
            .flatMap(member -> ViewerRole.teamRole(member.getRole()));
    }

    public static Optional<UUID> parse(final String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
