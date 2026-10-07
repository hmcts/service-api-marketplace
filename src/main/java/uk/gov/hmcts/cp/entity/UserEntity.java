package uk.gov.hmcts.cp.entity;

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
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private OrganisationEntity organisation;

    private String firstName;
    private String lastName;
    private String email;
    private String passwordHash;
    private String status;

    // 'consumer' or 'producer'; the column defaults to consumer, so rows that predate it read as one.
    private String role;

    // The object id of this person's user in Microsoft Entra, when one was created for them; null otherwise.
    private String entraObjectId;
}
