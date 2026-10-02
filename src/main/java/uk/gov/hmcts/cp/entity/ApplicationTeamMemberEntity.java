package uk.gov.hmcts.cp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Someone other than the owner who can work on an application, identified by email so they can be invited first. */
@Entity
@Table(name = "application_team_member")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApplicationTeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID publicId;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    private String email;

    // 'developer' or 'administrator'
    private String role;

    private LocalDateTime addedAt;

    @PrePersist
    void applyDefaults() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
