package uk.gov.hmcts.cp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketplace_user")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private OrganisationEntity organisation;

    private String firstName;
    private String lastName;
    private String email;
    private String passwordHash;
    private String status;

    // Entra External ID's object id (oid) for a person who signs in with Entra; null for everyone else.
    private String entraObjectId;

    // UTC. Set by the database on insert, whichever way the account was created, and never changed.
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // UTC. Each Entra sign-in; null until the first.
    private LocalDateTime lastSignedInAt;
}
