package uk.gov.hmcts.cp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_request")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SubscriptionRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String type;
    private String orgName;
    private String userName;
    private String userEmail;
    private String status;
    private LocalDateTime submittedAt;
    private String apiShortCode;
    private String api;
    private String environment;
    private String expectedVolume;
    private String useCase;
    private boolean oauth2Capable;
    private String declaration;
}
