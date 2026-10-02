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

@Entity
@Table(name = "application")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private String name;

    private String environment;

    // The Client ID: the real Entra appId for an application registered through Entra (the secret
    // Entra issued alongside it is never persisted here - shown once, exactly like Entra's own
    // behaviour), otherwise the application's own public id.
    private String clientId;

    private LocalDateTime createdAt;

    private String description;

    // What the application is called in URLs and responses. The bigserial id above never leaves
    // the service: a sequential number in a URL invites guessing.
    private UUID publicId;

    private String publicKeyUrl;

    private String callbackUrl;

    // JSON held as text (a flat object, and a list of {id, name}); read and written whole.
    private String customAttributes;

    private String connectedApis;

    // Filled in here rather than only by the column defaults: an INSERT from the entity names every
    // column, so a null in one of these would override the default and fail the NOT NULL. This also
    // keeps the older registration path, which builds the entity without them, working.
    @PrePersist
    void applyDefaults() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (customAttributes == null) {
            customAttributes = "{}";
        }
        if (connectedApis == null) {
            connectedApis = "[]";
        }
    }
}
