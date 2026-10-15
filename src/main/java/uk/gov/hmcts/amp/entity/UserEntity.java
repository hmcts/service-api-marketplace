package uk.gov.hmcts.amp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketplace_user")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    public static final String ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entraObjectId;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
