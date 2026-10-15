package uk.gov.hmcts.amp.domain;

import uk.gov.hmcts.amp.entity.UserEntity;

import java.time.LocalDateTime;

public record UserResponse(Long id, String entraObjectId, String email, String firstName, String lastName,
                           String status, LocalDateTime createdAt, LocalDateTime lastLoginAt) {

    public static UserResponse from(final UserEntity user) {
        return new UserResponse(user.getId(), user.getEntraObjectId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getStatus(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
