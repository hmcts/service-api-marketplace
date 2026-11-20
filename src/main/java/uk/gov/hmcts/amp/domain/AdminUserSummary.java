package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.UserEntity;

import java.time.LocalDateTime;

public record AdminUserSummary(Long id, String name, String email, LocalDateTime createdAt,
                               LocalDateTime lastLoginAt, long applicationCount) {

    public static AdminUserSummary from(final UserEntity user, final long applicationCount) {
        return new AdminUserSummary(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim(),
            user.getEmail(), user.getCreatedAt(), user.getLastLoginAt(), applicationCount);
    }
}