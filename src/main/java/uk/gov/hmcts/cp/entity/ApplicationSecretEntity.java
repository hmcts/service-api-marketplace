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

/** A client secret. Only its bcrypt hash and last four characters are kept; the secret itself is shown once. */
@Entity
@Table(name = "application_secret")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApplicationSecretEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID publicId;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    private String keyHash;

    private String keyPreview;

    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    @PrePersist
    void applyDefaults() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
