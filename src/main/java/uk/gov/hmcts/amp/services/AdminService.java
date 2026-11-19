package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.AdminUserSummary;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.entity.UserRole;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository.UserApplicationCount;
import uk.gov.hmcts.amp.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** What the Marketplace Team sees of everyone. Read only, and admins only. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    /** 403 rather than 404: the caller is known, they are just not allowed this. */
    public UserEntity requireAdmin(final String requestingUserId) {
        final UserEntity caller = userService.requireSignedIn(requestingUserId);
        if (caller.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only.");
        }
        return caller;
    }

    public List<AdminUserSummary> listUsers(final String requestingUserId) {
        requireAdmin(requestingUserId);
        final Map<Long, Long> applications = applicationRepository.countByUser().stream()
            .collect(Collectors.toMap(UserApplicationCount::getUserId, UserApplicationCount::getTotal));
        return userRepository.findByStatusOrderByCreatedAtDesc(UserEntity.ACTIVE).stream()
            .map(user -> AdminUserSummary.from(user, applications.getOrDefault(user.getId(), 0L)))
            .toList();
    }
}